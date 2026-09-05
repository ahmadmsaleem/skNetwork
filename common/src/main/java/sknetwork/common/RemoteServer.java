package sknetwork.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One server as the rest of the network sees it.
 * A backend reports its own, because it is the only thing that knows its whitelist
 * and its real player list. The proxy collects them and hands the whole set back.
 */
public record RemoteServer(String name, String motd, String version, int maxPlayers,
		List<String> players, List<String> whitelisted) {

	public static RemoteServer empty(String name) {
		return new RemoteServer(name, "", "", 0, List.of(), List.of());
	}

	public void write(PacketOut out) {
		out.string(name).string(motd).string(version).varInt(maxPlayers);
		out.varInt(players.size());
		players.forEach(out::string);
		out.varInt(whitelisted.size());
		whitelisted.forEach(out::string);
	}

	public static RemoteServer read(PacketIn in) throws IOException {
		String name = in.string();
		String motd = in.string();
		String version = in.string();
		int maxPlayers = in.varInt();
		return new RemoteServer(name, motd, version, maxPlayers, names(in), names(in));
	}

	private static List<String> names(PacketIn in) throws IOException {
		int count = in.varInt();
		if (count < 0 || count > 100_000)
			throw new IOException("player count " + count + " is out of range");

		List<String> names = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			names.add(in.string());
		return names;
	}
}
