package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sknetwork.common.Frame;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

final class NetworkState {

	private record Held(RemoteServer info, Object owner, long order) {
	}

	private final Map<String, Held> servers = new ConcurrentHashMap<>();
	private long reports;

	private volatile Map<String, String> holders = Map.of();

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

	private List<Held> newestFirst() {
		List<Held> held = new ArrayList<>(servers.values());
		held.sort(Comparator.comparingLong(Held::order).reversed());
		return held;
	}

	private void reindex() {
		Map<String, String> fresh = new HashMap<>();
		for (Held held : newestFirst())
			for (String player : held.info().players())
				fresh.putIfAbsent(player.toLowerCase(Locale.ROOT), held.info().name());
		holders = fresh;
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
		return holders.get(player.toLowerCase(Locale.ROOT));
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

	Frame frame() {
		List<RemoteServer> copy = all();
		PacketOut out = new PacketOut(Protocol.NETWORK_STATE).varInt(copy.size());
		for (RemoteServer server : copy)
			server.write(out);
		return out.frame();
	}
}
