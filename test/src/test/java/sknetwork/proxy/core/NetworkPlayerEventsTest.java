package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sknetwork.common.PlayerChange;
import sknetwork.common.RemoteServer;

class NetworkPlayerEventsTest {

	private final Object lobbySocket = new Object();
	private final Object survivalSocket = new Object();

	private final AtomicLong now = new AtomicLong(1_000);
	private final NetworkState state = new NetworkState(now::get);

	@Test
	void reportsSomebodyArrivingOnTheNetwork() {
		state.put(lobbySocket, server("lobby", "eult"));

		PlayerChange change = only(state.drain());

		assertEquals(PlayerChange.Kind.JOIN, change.kind());
		assertEquals("eult", change.player());
		assertNull(change.from());
		assertEquals("lobby", change.to());
	}

	@Test
	void holdsAQuitBackAndReleasesItOnceTheGraceIsUp() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.drain();

		state.put(lobbySocket, server("lobby"));

		assertTrue(state.drain().isEmpty(), "a quit must not fire the moment the name disappears");
		assertTrue(state.sweep().isEmpty(), "still inside the grace period");

		now.addAndGet(NetworkState.SWITCH_GRACE_MS);
		PlayerChange change = only(state.sweep());

		assertEquals(PlayerChange.Kind.QUIT, change.kind());
		assertEquals("lobby", change.from());
		assertNull(change.to());
	}

	@Test
	void readsAMoveAsOneSwitchWhenTheNewServerReportsFirst() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.drain();

		state.put(survivalSocket, server("survival", "eult"));
		PlayerChange change = only(state.drain());

		assertEquals(PlayerChange.Kind.SWITCH, change.kind());
		assertEquals("lobby", change.from());
		assertEquals("survival", change.to());

		state.put(lobbySocket, server("lobby"));
		assertTrue(state.drain().isEmpty(), "the late quit from the old server is not a second event");
	}

	@Test
	void readsAMoveAsOneSwitchWhenTheOldServerReportsFirst() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.drain();

		state.put(lobbySocket, server("lobby"));
		assertTrue(state.drain().isEmpty());

		state.put(survivalSocket, server("survival", "eult"));
		PlayerChange change = only(state.drain());

		assertEquals(PlayerChange.Kind.SWITCH, change.kind());
		assertEquals("lobby", change.from());
		assertEquals("survival", change.to());

		now.addAndGet(NetworkState.SWITCH_GRACE_MS * 10);
		assertTrue(state.sweep().isEmpty(), "the held quit was spent on the switch");
	}

	@Test
	void quitsEverybodyAServerWasHoldingWhenItDrops() {
		state.put(lobbySocket, server("lobby", "eult", "njol"));
		state.put(survivalSocket, server("survival", "notch"));
		state.drain();

		assertTrue(state.remove(lobbySocket, "lobby"));
		assertTrue(state.drain().isEmpty());

		now.addAndGet(NetworkState.SWITCH_GRACE_MS);
		List<PlayerChange> gone = state.sweep();

		assertEquals(2, gone.size());
		assertTrue(gone.stream().allMatch(change -> change.kind() == PlayerChange.Kind.QUIT));
		assertTrue(gone.stream().allMatch(change -> "lobby".equals(change.from())));
		assertEquals("survival", state.serverOf("notch"));
	}

	private static PlayerChange only(List<PlayerChange> changes) {
		assertEquals(1, changes.size(), "expected exactly one change, got " + changes);
		return changes.get(0);
	}

	private static RemoteServer server(String name, String... players) {
		return new RemoteServer(name, "m", "v", 20, List.of(players), List.of());
	}
}
