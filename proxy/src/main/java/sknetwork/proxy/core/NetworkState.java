package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import sknetwork.common.Frame;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerChange;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

final class NetworkState {

	static final long SWITCH_GRACE_MS = 1_000;

	private record Held(RemoteServer info, Object owner, long order) {
	}

	private record Placement(String name, String server) {
	}

	private record Leaving(String name, String from, long since) {
	}

	private final Map<String, Held> servers = new ConcurrentHashMap<>();
	private long reports;

	private volatile Map<String, Placement> holders = Map.of();

	private final Map<String, Leaving> leaving = new LinkedHashMap<>();
	private final List<PlayerChange> pending = new ArrayList<>();
	private final LongSupplier clock;

	NetworkState() {
		this(System::currentTimeMillis);
	}

	NetworkState(LongSupplier clock) {
		this.clock = clock;
	}

	void put(Object owner, RemoteServer server) {
		servers.put(server.name(), new Held(server, owner, ++reports));
		reindex();
	}

	boolean remove(Object owner, String name) {
		Held held = servers.get(name);
		if (held == null || held.owner() != owner)
			return false;
		if (!servers.remove(name, held))
			return false;
		reindex();
		return true;
	}

	List<PlayerChange> drain() {
		if (pending.isEmpty())
			return List.of();
		List<PlayerChange> drained = List.copyOf(pending);
		pending.clear();
		return drained;
	}

	List<PlayerChange> sweep() {
		long now = clock.getAsLong();
		List<PlayerChange> gone = new ArrayList<>();

		Iterator<Map.Entry<String, Leaving>> waiting = leaving.entrySet().iterator();
		while (waiting.hasNext()) {
			Leaving left = waiting.next().getValue();
			if (now - left.since() < SWITCH_GRACE_MS)
				continue;
			waiting.remove();
			gone.add(PlayerChange.quit(left.name(), left.from()));
		}
		return gone;
	}

	private List<Held> newestFirst() {
		List<Held> held = new ArrayList<>(servers.values());
		held.sort(Comparator.comparingLong(Held::order).reversed());
		return held;
	}

	private void reindex() {
		Map<String, Placement> previous = holders;
		Map<String, Placement> fresh = new HashMap<>();
		for (Held held : newestFirst())
			for (String player : held.info().players())
				fresh.putIfAbsent(key(player), new Placement(player, held.info().name()));
		holders = fresh;

		long now = clock.getAsLong();

		for (Map.Entry<String, Placement> entry : fresh.entrySet()) {
			Placement is = entry.getValue();
			Placement was = previous.get(entry.getKey());

			if (was != null) {
				if (!was.server().equals(is.server()))
					pending.add(PlayerChange.switched(is.name(), was.server(), is.server()));
				continue;
			}

			Leaving left = leaving.remove(entry.getKey());
			if (left == null)
				pending.add(PlayerChange.joined(is.name(), is.server()));
			else if (!left.from().equals(is.server()))
				pending.add(PlayerChange.switched(is.name(), left.from(), is.server()));
		}

		for (Map.Entry<String, Placement> entry : previous.entrySet()) {
			if (fresh.containsKey(entry.getKey()))
				continue;
			Placement was = entry.getValue();
			leaving.putIfAbsent(entry.getKey(), new Leaving(was.name(), was.server(), now));
		}
	}

	private static String key(String player) {
		return player.toLowerCase(Locale.ROOT);
	}

	List<RemoteServer> all() {
		List<RemoteServer> all = new ArrayList<>(servers.size());
		for (Held held : newestFirst())
			all.add(held.info());
		return all;
	}

	int size() {
		return servers.size();
	}

	String serverOf(String player) {
		Placement placement = holders.get(key(player));
		return placement == null ? null : placement.server();
	}

	Map<String, List<String>> route(Iterable<String> players) {
		Map<String, List<String>> byServer = new LinkedHashMap<>();
		for (String player : players) {
			String server = serverOf(player);
			if (server != null)
				byServer.computeIfAbsent(server, key -> new ArrayList<>()).add(player);
		}
		return byServer;
	}

	List<String> names() {
		List<String> names = new ArrayList<>(servers.keySet());
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	boolean holds(String server) {
		return servers.containsKey(server);
	}

	static Frame empty() {
		return new PacketOut(Protocol.NETWORK_STATE).varInt(0).frame();
	}

	Frame frame() {
		List<RemoteServer> copy = all();
		PacketOut out = new PacketOut(Protocol.NETWORK_STATE).varInt(copy.size());
		for (RemoteServer server : copy)
			server.write(out);
		return out.frame();
	}
}
