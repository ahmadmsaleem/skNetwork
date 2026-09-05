package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sknetwork.common.RemoteServer;

class NetworkStateTest {

	private final Object lobbySocket = new Object();
	private final Object survivalSocket = new Object();
	private final NetworkState state = new NetworkState();

	@Test
	void findsAPlayerWhateverTheCase() {
		state.put(lobbySocket, server("lobby", "Eult"));

		assertEquals("lobby", state.serverOf("eult"));
		assertEquals("lobby", state.serverOf("EULT"));
		assertNull(state.serverOf("njol"));
	}

	@Test
	void trustsTheNewestReportWhenTwoServersNameThePlayer() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.put(survivalSocket, server("survival", "eult"));

		assertEquals("survival", state.serverOf("eult"));
		assertEquals("survival", state.all().get(0).name());

		state.put(lobbySocket, server("lobby"));
		assertEquals("survival", state.serverOf("eult"));
	}

	@Test
	void forgetsThePlayersOfAServerThatLeaves() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.put(survivalSocket, server("survival", "njol"));

		assertTrue(state.remove(lobbySocket, "lobby"));

		assertNull(state.serverOf("eult"));
		assertEquals("survival", state.serverOf("njol"));
	}

	@Test
	void removesOnlyWhatTheSameOwnerReported() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.put(survivalSocket, server("lobby", "eult"));

		assertFalse(state.remove(lobbySocket, "lobby"));
		assertTrue(state.holds("lobby"));
		assertEquals("lobby", state.serverOf("eult"));

		assertTrue(state.remove(survivalSocket, "lobby"));
		assertFalse(state.holds("lobby"));
	}

	@Test
	void groupsTargetsByTheServerHoldingThem() {
		state.put(lobbySocket, server("lobby", "a", "b"));
		state.put(survivalSocket, server("survival", "c"));

		Map<String, List<String>> routed = state.route(List.of("A", "c", "b", "nobody"));

		assertEquals(List.of("A", "b"), routed.get("lobby"));
		assertEquals(List.of("c"), routed.get("survival"));
		assertEquals(2, routed.size());
	}

	private static RemoteServer server(String name, String... players) {
		return new RemoteServer(name, "m", "v", 20, List.of(players), List.of());
	}
}
