package sknetwork.spigot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import sknetwork.common.PacketIn;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

class NetworkCacheTest {

	private final NetworkCache cache = new NetworkCache();

	@Test
	void startsKnowingNothing() {
		assertNull(cache.serverOf("eult"));
		assertFalse(cache.isOnline("lobby"));
		assertTrue(cache.names().isEmpty());
		assertTrue(cache.players(null).isEmpty());
	}

	@Test
	void findsAPlayerWhateverTheCase() throws IOException {
		cache.replace(frame(server("lobby", "Eult"), server("survival", "njol")));

		assertEquals("lobby", cache.serverOf("EULT"));
		assertEquals("survival", cache.serverOf("Njol"));
		assertNull(cache.serverOf("nobody"));
	}

	@Test
	void keepsTheFirstServerNamingAPlayer() throws IOException {
		cache.replace(frame(server("survival", "eult"), server("lobby", "eult")));

		assertEquals("survival", cache.serverOf("eult"));
	}

	@Test
	void listsServersInOrderAndPlayersPerServer() throws IOException {
		cache.replace(frame(server("survival", "c"), server("Lobby", "a", "b")));

		assertEquals(List.of("Lobby", "survival"), cache.names());
		assertEquals(List.of("a", "b"), cache.players("Lobby"));
		assertEquals(3, cache.players(null).size());
		assertTrue(cache.players("ghost").isEmpty());
		assertEquals(20, cache.server("Lobby").maxPlayers());
	}

	@Test
	void forgetsEverythingWhenTheProxyIsLost() throws IOException {
		cache.replace(frame(server("lobby", "eult")));

		cache.clear();

		assertNull(cache.serverOf("eult"));
		assertFalse(cache.isOnline("lobby"));
	}

	@Test
	void refusesAnAbsurdCount() {
		PacketIn packet = new PacketIn(new PacketOut(Protocol.NETWORK_STATE).varInt(20_000).frame().payload);
		try {
			cache.replace(packet);
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("out of range"));
			return;
		}
		throw new AssertionError("a count of 20000 was accepted");
	}

	private static PacketIn frame(RemoteServer... servers) {
		PacketOut out = new PacketOut(Protocol.NETWORK_STATE).varInt(servers.length);
		for (RemoteServer server : servers)
			server.write(out);
		return new PacketIn(out.frame().payload);
	}

	private static RemoteServer server(String name, String... players) {
		return new RemoteServer(name, "m", "v", 20, List.of(players), List.of());
	}
}
