package sknetwork.spigot;

import java.io.IOException;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import sknetwork.common.Frame;
import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import sknetwork.common.PacketIn;
import sknetwork.common.Protocol;
import sknetwork.common.Throttle;
import sknetwork.common.VariableName;
import sknetwork.spigot.elements.types.AtomicResult;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Drains frames onto the main thread, capped per tick so a large snapshot
 * spreads over several ticks instead of freezing the server.
 */
final class DeltaApplier extends BukkitRunnable {

	private static final int PER_TICK = 2_000;

	private final SkNetworkSpigot plugin;
	private final Queue<Frame> inbound;

	/**
	 * Every network variable this server thinks exists. A snapshot only ever adds,
	 * so without this a key deleted while we were away would stay here forever.
	 */
	private final Set<String> mirrored = new HashSet<>();

	/**
	 * Writes the proxy never took, because this server was not synced at the time.
	 * Skript put them in its own map anyway, so they disagree with the network
	 * until a full snapshot puts them right.
	 */
	private final Set<String> unsynced = ConcurrentHashMap.newKeySet();

	/** Set by {@code /sknet resync}: pull everything rather than resume. */
	private volatile boolean forceFull;

	private Set<String> arriving;
	private long applied;
	private long dropped;
	private int snapshotEntries;
	private final Throttle unreadableWarnings = new Throttle(10_000);

	DeltaApplier(SkNetworkSpigot plugin, Queue<Frame> inbound) {
		this.plugin = plugin;
		this.inbound = inbound;
	}

	long applied() {
		return applied;
	}

	long dropped() {
		return dropped;
	}

	int mirroredCount() {
		return mirrored.size();
	}

	void markUnsynced(String localName) {
		unsynced.add(localName);
	}

	void requestFullSnapshot() {
		forceFull = true;
	}

	/** Whether a resume would leave this server holding values the network refused. */
	boolean needsFullSnapshot() {
		return forceFull || !unsynced.isEmpty();
	}

	@Override
	public void run() {
		int budget = PER_TICK;

		while (budget > 0) {
			Frame frame = inbound.poll();
			if (frame == null)
				break;

			try {
				budget -= handle(frame);
			} catch (IOException e) {
				plugin.getLogger().warning("malformed frame from the proxy (opcode 0x"
						+ Integer.toHexString(frame.opcode & 0xFF) + "): " + e.getMessage());
			} catch (RuntimeException e) {
				// one unreadable change must not abandon the rest of the tick
				plugin.getLogger().warning("failed to apply a change from the proxy: " + e);
				budget--;
			}
		}

		// after the drain, so a reply that arrived this tick is never counted late
		plugin.requests().sweep(plugin.isSynced());
	}

	private int handle(Frame frame) throws IOException {
		PacketIn packet = frame.reader();

		switch (frame.opcode) {
			case Protocol.SNAPSHOT -> {
				packet.bool(); // 'last', unused: readiness comes from SYNCED instead
				int count = packet.varInt();

				if (arriving == null)
					arriving = new HashSet<>();
				for (int i = 0; i < count; i++) {
					String wireName = packet.string();
					apply(MutationMode.SET, wireName, packet.nullableString(), packet.nullableBytes());
					arriving.add(plugin.prefix() + wireName);
				}
				snapshotEntries += count;
				return Math.max(count, 1);
			}
			case Protocol.SYNCED -> {
				long seq = packet.int64();
				boolean fullSnapshot = packet.bool();

				int removed = fullSnapshot ? reconcile() : 0;
				plugin.client().setLastSeq(seq);
				plugin.client().markReady();

				if (fullSnapshot)
					plugin.getLogger().info("synced at seq " + seq + ": " + snapshotEntries
							+ " variable(s) received"
							+ (removed == 0 ? "" : ", " + removed + " stale one(s) dropped"));
				else
					plugin.getLogger().info("resynced at seq " + seq + ", mirror kept");

				snapshotEntries = 0;
				arriving = null;
				plugin.onSynced();
				return 1;
			}
			case Protocol.DELTA -> {
				long seq = packet.int64();
				MutationMode mode = MutationMode.byId((byte) packet.varInt());
				String name = packet.string();
				String type = packet.nullableString();
				byte[] value = packet.nullableBytes();

				// advance even when it could not be applied. asking again on the next
				// reconnect would fail the same way.
				try {
					apply(mode, name, type, value);
				} finally {
					plugin.client().setLastSeq(seq);
				}
				return 1;
			}
			case Protocol.RESULT -> {
				long requestId = packet.int64();
				boolean ok = packet.bool();
				packet.int64(); // seq, which only means anything on the proxy
				String type = packet.nullableString();
				byte[] value = packet.nullableBytes();
				String error = packet.nullableString();

				AtomicResult result = ok
						? AtomicResult.accepted(SkriptBridge.read(type, value))
						: AtomicResult.refused(error);

				// fire and forget has nobody to tell, so a refusal stays in the log.
				// one that was waited on reaches the script.
				if (!plugin.requests().complete(requestId, result) && !ok)
					plugin.getLogger().warning("the proxy refused an atomic change: " + error);
				return 1;
			}
			case Protocol.PONG -> {
				plugin.client().recordPong(packet.int64());
				return 1;
			}
			case Protocol.MANIFEST -> {
				ScriptSync sync = plugin.scripts();
				if (sync != null)
					sync.onManifest(Manifest.read(packet));
				return 1;
			}
			case Protocol.FILE -> {
				long version = packet.int64();
				String path = packet.string();
				byte[] content = packet.nullableBytes();
				ScriptSync sync = plugin.scripts();
				if (sync != null)
					sync.onFile(version, path, content);
				return 1;
			}
			default -> {
				plugin.getLogger().warning("ignoring unexpected opcode 0x"
						+ Integer.toHexString(frame.opcode & 0xFF) + " from the proxy");
				return 1;
			}
		}
	}

	/**
	 * Deletes anything we hold that the snapshot did not mention, which is what
	 * this server missed while away.
	 *
	 * @return how many were dropped
	 */
	private int reconcile() {
		// a refused write is not in the mirror, so without this the key survives the
		// snapshot and this server keeps a value nobody else has
		mirrored.addAll(unsynced);
		unsynced.clear();
		forceFull = false;

		Set<String> stale = new HashSet<>(mirrored);
		if (arriving != null)
			stale.removeAll(arriving);

		for (String name : stale) {
			SkriptBridge.applyDelete(name);
			mirrored.remove(name);
			applied++;
		}
		return stale.size();
	}

	private void apply(MutationMode mode, String wireName, String type, byte[] value) {
		// with no storage registered, Skript hands the variable to the next database
		// that accepts the name: the catch-all CSV one. that writes network variables
		// to local disk and then dies on a null source. dropping is the only safe move.
		if (SkNetworkStorage.instance() == null) {
			dropped++;
			return;
		}

		String localName = plugin.prefix() + wireName;

		if (mode == MutationMode.DELETE || value == null) {
			SkriptBridge.applyDelete(localName);
			forget(localName);
			applied++;
			return;
		}

		if (!SkriptBridge.applySet(localName, type, value, SkNetworkStorage.instance())) {
			dropped++;
			warnUnreadable(localName, type);
			return;
		}
		mirrored.add(localName);
		applied++;
	}

	private void warnUnreadable(String localName, String type) {
		if (!unreadableWarnings.allow())
			return;
		plugin.getLogger().warning("cannot read {" + localName + "} of type '" + type
				+ "' on this server, so it is missing from the mirror. " + dropped
				+ " change(s) dropped so far. Every server needs the same Skript and plugin versions."
				+ " Some Skript types, itemtypes carrying item meta among them, do not survive"
				+ " being serialised at all.");
	}

	private void forget(String localName) {
		if (!VariableName.isTree(localName)) {
			mirrored.remove(localName);
			return;
		}

		String base = VariableName.treeBase(localName);
		mirrored.removeIf(name -> VariableName.inTree(name, base));
	}
}
