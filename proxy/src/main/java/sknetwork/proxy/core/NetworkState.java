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

/**
 * What every backend has said about itself. The proxy never asks its own platform
 * for this, so BungeeCord and Velocity report identically and a server's whitelist
 * is the one it actually holds.
 * Each entry remembers which connection reported it. A backend that hard reboots
 * sends no FIN, so its old socket sits here until a write to it fails, by which
 * time the same server is back on a new one. Without the owner, the old socket
 * finally closing would take the live server's entry with it.
 */
final class NetworkState {

	private record Held(RemoteServer info, Object owner, long order) {
	}

	private final Map<String, Held> servers = new ConcurrentHashMap<>();
	private long reports;

	private volatile Map<String, String> holders = Map.of();

	/** @param owner whoever reported it, so only that connection going away removes it */
	void put(Object owner, RemoteServer server) {
		servers.put(server.name(), new Held(server, owner, ++reports));
		reindex();
	}

	/** @return whether anything was removed; nothing is when another owner has since taken the name */
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

	/** @return the server that player is on, or null if nobody is holding them */
	String serverOf(String player) {
		return holders.get(player.toLowerCase(Locale.ROOT));
	}

	/** Groups the targets by the server holding each one, so each backend is told once. */
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

	/**
	 * Copies first, then counts. Reading the size and then walking the map lets a
	 * server added or removed in between leave the count wrong, and a backend then
	 * reads one entry too many or too few out of the frame.
	 */
	Frame frame() {
		List<RemoteServer> copy = all();
		PacketOut out = new PacketOut(Protocol.NETWORK_STATE).varInt(copy.size());
		for (RemoteServer server : copy)
			server.write(out);
		return out.frame();
	}
}
