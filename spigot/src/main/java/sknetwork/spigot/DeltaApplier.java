package sknetwork.spigot;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import sknetwork.common.Frame;
import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import sknetwork.common.NetworkSound;
import sknetwork.common.NetworkTabList;
import sknetwork.common.NetworkTitle;
import sknetwork.common.PacketIn;
import sknetwork.common.PingSettings;
import sknetwork.common.Protocol;
import sknetwork.common.PlayerAction;
import sknetwork.common.PlayerChange;
import sknetwork.common.Throttle;
import sknetwork.common.VariableName;
import sknetwork.spigot.elements.events.NetworkPlayerJoinEvent;
import sknetwork.spigot.elements.events.NetworkPlayerQuitEvent;
import sknetwork.spigot.elements.events.NetworkServerSwitchEvent;
import sknetwork.spigot.elements.events.NetworkVariableChangeEvent;
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.AtomicResult;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

final class DeltaApplier extends BukkitRunnable {

	private static final int PER_TICK = 2_000;

	private static final String EMPTY_JSON = "{\"text\":\"\"}";

	private final SkNetworkSpigot plugin;
	private final Queue<Frame> inbound;

	private final Set<String> mirrored = new HashSet<>();

	private final Set<String> unsynced = ConcurrentHashMap.newKeySet();

	private volatile boolean forceFull;

	private volatile boolean sessionEnded;

	private Set<String> arriving;
	private Object previous;
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

	void sessionEnded() {
		sessionEnded = true;
	}

	boolean needsFullSnapshot() {
		return forceFull || !unsynced.isEmpty();
	}

	@Override
	public void run() {
		int budget = PER_TICK;

		if (sessionEnded) {
			sessionEnded = false;
			arriving = null;
			snapshotEntries = 0;
		}

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

		plugin.requests().sweep(plugin.isSynced());
	}

	private int handle(Frame frame) throws IOException {
		PacketIn packet = frame.reader();

		switch (frame.opcode) {
			case Protocol.SNAPSHOT -> {
				int count = packet.varInt();

				if (arriving == null)
					arriving = new HashSet<>();
				for (int i = 0; i < count; i++) {
					String wireName = packet.string();
					apply(MutationMode.SET, wireName, packet.nullableString(), packet.nullableBytes());
					arriving.add(localName(wireName));
				}
				snapshotEntries += count;
				return Math.max(count, 1);
			}
			case Protocol.SYNCED -> {
				long seq = packet.int64();
				boolean fullSnapshot = packet.bool();

				int removed = fullSnapshot ? reconcile() : 0;
				int received = snapshotEntries;
				snapshotEntries = 0;
				arriving = null;
				plugin.client().setLastSeq(seq);
				if (!plugin.client().markReady())
					return 1;

				if (fullSnapshot)
					plugin.getLogger().info("synced at seq " + seq + ": " + received
							+ " variable(s) received"
							+ (removed == 0 ? "" : ", " + removed + " stale one(s) dropped"));
				else
					plugin.getLogger().info("resynced at seq " + seq + ", mirror kept");

				plugin.onSynced();
				return 1;
			}
			case Protocol.DELTA -> {
				long seq = packet.int64();
				MutationMode mode = MutationMode.byId((byte) packet.varInt());
				String name = packet.string();
				String type = packet.nullableString();
				byte[] value = packet.nullableBytes();
				String wasType = packet.nullableString();
				byte[] wasValue = packet.nullableBytes();

				try {
					previous = wasValue == null ? null : SkriptBridge.read(wasType, wasValue);
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
			case Protocol.NETWORK_STATE -> {
				plugin.network().replace(packet);
				return 1;
			}
			case Protocol.PLAYER_DELIVERY -> {
				deliver(packet);
				return 1;
			}
			case Protocol.PING_STATE -> {
				plugin.ping(PingSettings.read(packet));
				return 1;
			}
			case Protocol.PLAYER_EVENT -> {
				playerEvents(packet);
				return 1;
			}
			case Protocol.CONSOLE_COMMAND -> {
				String command = packet.string();
				plugin.getLogger().info("running '" + command + "' for the network");
				org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
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

	private void playerEvents(PacketIn packet) throws IOException {
		int count = packet.varInt();
		if (count < 0 || count > 100_000)
			throw new IOException("player change count " + count + " is out of range");

		List<PlayerChange> changes = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			changes.add(PlayerChange.read(packet));

		if (!plugin.isSynced())
			return;

		for (PlayerChange change : changes) {
			NetworkPlayer player = NetworkPlayer.named(change.player());
			if (player == null)
				continue;

			org.bukkit.Bukkit.getPluginManager().callEvent(switch (change.kind()) {
				case JOIN -> new NetworkPlayerJoinEvent(player, change.to());
				case QUIT -> new NetworkPlayerQuitEvent(player, change.from());
				case SWITCH -> new NetworkServerSwitchEvent(player, change.from(), change.to());
			});
		}
	}

	private void deliver(PacketIn packet) throws IOException {
		PlayerAction action = PlayerAction.byId((byte) packet.varInt());
		int count = packet.varInt();
		Set<String> named = new HashSet<>();
		for (int i = 0; i < count; i++)
			named.add(packet.string().toLowerCase(Locale.ROOT));
		byte[] body = packet.nullableBytes();
		if (body == null)
			return;

		if (named.isEmpty() && action == PlayerAction.MESSAGE)
			plugin.getServer().getConsoleSender()
					.sendMessage(NetworkText.fromJson(new PacketIn(body).string()));

		for (Player player : plugin.getServer().getOnlinePlayers()) {
			if (!named.isEmpty() && !named.contains(player.getName().toLowerCase(Locale.ROOT)))
				continue;
			apply(player, action, new PacketIn(body));
		}
	}

	private void apply(Player player, PlayerAction action, PacketIn body) throws IOException {
		switch (action) {
			case MESSAGE -> player.sendMessage(NetworkText.fromJson(body.string()));
			case ACTION_BAR -> player.sendActionBar(NetworkText.fromJson(body.string()));
			case TITLE -> {
				NetworkTitle title = NetworkTitle.read(body);
				player.showTitle(Title.title(
						NetworkText.fromJson(title.title() == null ? EMPTY_JSON : title.title()),
						NetworkText.fromJson(title.subtitle() == null ? EMPTY_JSON : title.subtitle()),
						Times.times(ticks(title.fadeIn()), ticks(title.stay()), ticks(title.fadeOut()))));
			}
			case SOUND -> {
				NetworkSound sound = NetworkSound.read(body);
				player.playSound(player.getLocation(), sound.key(), sound.volume(), sound.pitch());
			}
			case TAB_LIST -> {
				NetworkTabList tab = NetworkTabList.read(body);
				if (tab.header() != null)
					player.sendPlayerListHeader(NetworkText.fromJson(tab.header()));
				if (tab.footer() != null)
					player.sendPlayerListFooter(NetworkText.fromJson(tab.footer()));
			}
			default -> {
			}
		}
	}

	private static Duration ticks(int ticks) {
		return Duration.ofMillis(Math.max(ticks, 0) * 50L);
	}

	/** @return how many were dropped */
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

		// the proxy keeps names byte for byte, and one with a capital in it can arrive
		// from a jar older than the fix in EffAtomic. Skript lowercases the name in
		// every read, so it goes into the map lowercased too or no script can see it
		wireName = SkriptBridge.normalize(wireName);
		String localName = localName(wireName);

		if (mode == MutationMode.DELETE || value == null) {
			SkriptBridge.applyDelete(localName);
			forget(localName);
			applied++;
			announce(wireName, null);
			return;
		}

		if (!SkriptBridge.applySet(localName, type, value, SkNetworkStorage.instance())) {
			dropped++;
			warnUnreadable(localName, type);
			return;
		}
		mirrored.add(localName);
		applied++;
		announce(wireName, SkriptBridge.read(type, value));
	}

	/** The name Skript sees, prefix included and cased the way Skript cases it. */
	private String localName(String wireName) {
		return SkriptBridge.normalize(plugin.prefix() + wireName);
	}

	/** A snapshot is not a change, so nothing fires while one is arriving. */
	private void announce(String wireName, Object now) {
		if (arriving != null || !plugin.isSynced())
			return;
		org.bukkit.Bukkit.getPluginManager().callEvent(
				new NetworkVariableChangeEvent(wireName, now, previous));
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
