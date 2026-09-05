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

/**
 * This server's copy of what the proxy knows about the network. Read straight out
 * of memory, like the variable mirror, so an expression never waits on the wire.
 */
public final class NetworkCache {

	private record Picture(Map<String, RemoteServer> servers, Map<String, String> holders) {
	}

	private static final Picture EMPTY = new Picture(Map.of(), Map.of());

	private volatile Picture picture = EMPTY;

	void replace(PacketIn packet) throws IOException {
		int count = packet.varInt();
		if (count < 0 || count > 10_000)
			throw new IOException("server count " + count + " is out of range");

		Map<String, RemoteServer> servers = new LinkedHashMap<>(Math.max(count, 1));
		Map<String, String> holders = new HashMap<>();
		for (int i = 0; i < count; i++) {
			RemoteServer server = RemoteServer.read(packet);
			servers.put(server.name(), server);
			for (String player : server.players())
				holders.putIfAbsent(player.toLowerCase(Locale.ROOT), server.name());
		}
		picture = new Picture(servers, holders);
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
}
