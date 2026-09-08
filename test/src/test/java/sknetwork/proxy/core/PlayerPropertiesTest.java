package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import sknetwork.common.PlayerProperties;
import sknetwork.common.RemoteServer;

class PlayerPropertiesTest {

	private final Object lobbySocket = new Object();
	private final AtomicLong now = new AtomicLong(100_000);
	private final NetworkState state = new NetworkState(now::get);

	@Test
	void passesOnOnlyTheRowsThatMoved() {
		state.put(lobbySocket, server("lobby", "eult", "njol"));

		assertEquals(2, state.merge(List.of(row("eult", 40), row("njol", 60))).size());

		List<PlayerProperties> second = state.merge(List.of(row("eult", 40), row("njol", 60)));

		assertTrue(second.isEmpty(), "nothing changed, so nothing should go back out");
	}

	/** Ping wobble is most of the traffic if every millisecond counts as a change. */
	@Test
	void ignoresPingJitterButNoticesARealMove() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.merge(List.of(row("eult", 40)));

		assertTrue(state.merge(List.of(row("eult", 42))).isEmpty(), "2ms is jitter");
		assertTrue(state.merge(List.of(row("eult", 44))).isEmpty(), "4ms is jitter");

		List<PlayerProperties> moved = state.merge(List.of(row("eult", 45)));

		assertEquals(1, moved.size());
		assertEquals(45, moved.get(0).ping());
	}

	@Test
	void noticesADetailThatIsNotThePing() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.merge(List.of(row("eult", 40)));

		List<PlayerProperties> renamed = state.merge(List.of(new PlayerProperties(
				"eult", 40, "127.0.0.1", "§aRenamed", "en_us", 1_000)));

		assertEquals(1, renamed.size());
		assertEquals("§aRenamed", renamed.get(0).displayName());
	}

	/** Playtime travels once and every reader counts up from it. */
	@Test
	void bringsPlaytimeUpToDateWhenItIsHandedOn() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.merge(List.of(row("eult", 40)));

		now.addAndGet(60_000);
		PlayerProperties fresh = state.propertiesNow().get(0);

		assertEquals(1_000 + 1_200, fresh.playtimeTicks());
	}

	@Test
	void forgetsSomebodyWhoHasLeftTheNetwork() {
		state.put(lobbySocket, server("lobby", "eult"));
		state.merge(List.of(row("eult", 40)));
		assertEquals(1, state.propertiesNow().size());

		state.put(lobbySocket, server("lobby"));
		now.addAndGet(NetworkState.SWITCH_GRACE_MS);
		state.sweep();
		state.put(lobbySocket, server("lobby"));

		assertTrue(state.propertiesNow().isEmpty());
		assertNull(state.serverOf("eult"));
		assertFalse(state.holds("nobody"));
	}

	private static PlayerProperties row(String name, int ping) {
		return new PlayerProperties(name, ping, "127.0.0.1", "§f" + name, "en_us", 1_000);
	}

	private static RemoteServer server(String name, String... players) {
		return new RemoteServer(name, "m", "v", 20, List.of(players), List.of());
	}
}
