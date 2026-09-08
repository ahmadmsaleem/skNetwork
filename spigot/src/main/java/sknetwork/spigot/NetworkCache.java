package sknetwork.spigot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sknetwork.common.PacketIn;
import sknetwork.common.RemoteServer;
import sknetwork.spigot.elements.types.NetworkPlayer;

/**
 * This server's copy of what the proxy knows about the network. Read straight out
 * of memory, like the variable mirror, so an expression never waits on the wire.
 */
public final class NetworkCache {

	private record Picture(Map<String, RemoteServer> servers, Map<String, String> holders,
			Map<String, NetworkPlayer> byName, List<NetworkPlayer> all) {
	}

	private static final Picture EMPTY = new Picture(Map.of(), Map.of(), Map.of(), List.of());

	private volatile Picture picture = EMPTY;

	void replace(PacketIn packet) throws IOException {
		int count = packet.varInt();
		if (count < 0 || count > 10_000)
			throw new IOException("server count " + count + " is out of range");

		Map<String, RemoteServer> servers = new LinkedHashMap<>(Math.max(count, 1));
		Map<String, String> holders = new HashMap<>();
		Map<String, NetworkPlayer> byName = new LinkedHashMap<>();
		List<NetworkPlayer> all = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			RemoteServer server = RemoteServer.read(packet);
			servers.put(server.name(), server);
			for (String player : server.players()) {
				String key = player.toLowerCase(Locale.ROOT);
				holders.putIfAbsent(key, server.name());
				NetworkPlayer wrapped = NetworkPlayer.named(player);
				if (wrapped != null && byName.putIfAbsent(key, wrapped) == null)
					all.add(wrapped);
			}
		}
		picture = new Picture(servers, holders, byName, all);
	}

	void clear() {
		picture = EMPTY;
	}

	public RemoteServer server(String name) {
		return picture.servers().get(name);
	}

	public boolean isOnline(String name) {
		return picture.servers().containsKey(name);
	}

	public List<String> names() {
		List<String> names = new ArrayList<>(picture.servers().keySet());
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	/** @param server null for every server on the network */
	public List<String> players(String server) {
		Map<String, RemoteServer> servers = picture.servers();
		if (server != null) {
			RemoteServer one = servers.get(server);
			return one == null ? List.of() : new ArrayList<>(one.players());
		}

		List<String> all = new ArrayList<>();
		for (RemoteServer each : servers.values())
			all.addAll(each.players());
		return all;
	}

	/** @return the server holding that player, or null if nobody is */
	public String serverOf(String player) {
		return picture.holders().get(player.toLowerCase(Locale.ROOT));
	}

	/** @param server null for every server on the network */
	public List<NetworkPlayer> networkPlayers(String server) {
		if (server == null)
			return List.copyOf(picture.all());

		RemoteServer one = picture.servers().get(server);
		if (one == null)
			return List.of();

		Map<String, NetworkPlayer> byName = picture.byName();
		List<NetworkPlayer> found = new ArrayList<>(one.players().size());
		for (String player : one.players()) {
			NetworkPlayer known = byName.get(player.toLowerCase(Locale.ROOT));
			if (known != null)
				found.add(known);
		}
		return found;
	}
}
