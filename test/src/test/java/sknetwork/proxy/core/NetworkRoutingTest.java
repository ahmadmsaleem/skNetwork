package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sknetwork.common.PlayerAction;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

class NetworkRoutingTest {

	private static final String TOKEN = "a-test-token";

	private RecordingLog log;
	private NetworkServer server;
	private int port;
	private final List<FakeBackend> backends = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		log = new RecordingLog();
		server = start(true, false);
	}

	@AfterEach
	void tearDown() {
		backends.forEach(FakeBackend::close);
		backends.clear();
		if (server != null)
			server.stop();
	}

	@Test
	void handsANewBackendTheStateItAlreadyHas() throws IOException {
		synced("lobby").sendServerInfo(new RemoteServer("lobby", "A Lobby", "26.1.2", 50,
				List.of("eult"), List.of()));

		FakeBackend survival = synced("survival");
		List<RemoteServer> state = survival.networkState();

		assertEquals(1, state.size());
		assertEquals("lobby", state.get(0).name());
		assertEquals("A Lobby", state.get(0).motd());
		assertEquals(List.of("eult"), state.get(0).players());
	}

	@Test
	void tellsEveryServerWhenOneReportsItself() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.networkState();
		survival.networkState();

		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));

		assertEquals(1, survival.networkState().size());
		assertEquals(1, lobby.networkState().size());
	}

	@Test
	void forgetsAServerThatGoesAway() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of(), List.of()));
		survival.networkState();

		lobby.close();

		List<RemoteServer> state = survival.networkState();
		for (int i = 0; i < 8 && !state.isEmpty(); i++)
			state = survival.networkState();
		assertTrue(state.isEmpty());
	}

	@Test
	void sendsAMessageOnlyToTheServerHoldingThatPlayer() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));
		survival.sendServerInfo(new RemoteServer("survival", "m", "v", 20, List.of("njol"), List.of()));
		awaitState(2);

		survival.playerAction(PlayerAction.MESSAGE, List.of("eult"), "{\"text\":\"hi\"}");

		FakeBackend.Delivery delivered = lobby.delivery();
		assertEquals(PlayerAction.MESSAGE, delivered.action());
		assertEquals(List.of("eult"), delivered.targets());
		assertEquals("{\"text\":\"hi\"}", delivered.payload());
	}

	@Test
	void dropsAMessageForSomebodyNobodyIsHolding() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));
		lobby.networkState();

		lobby.playerAction(PlayerAction.MESSAGE, List.of("nobody"), "{\"text\":\"hi\"}");
		lobby.ping(7);

		assertEquals(7, lobby.ping(7));
	}

	@Test
	void sendsABroadcastToEveryBackend() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		lobby.playerAction(PlayerAction.MESSAGE, List.of(), "{\"text\":\"all\"}");

		assertEquals("{\"text\":\"all\"}", lobby.delivery().payload());
		assertEquals("{\"text\":\"all\"}", survival.delivery().payload());
	}

	@Test
	void groupsTargetsSoEachServerIsToldOnce() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("a", "b"), List.of()));
		survival.sendServerInfo(new RemoteServer("survival", "m", "v", 20, List.of("c"), List.of()));
		awaitState(2);

		survival.playerAction(PlayerAction.ACTION_BAR, List.of("a", "b", "c"), "{\"text\":\"x\"}");

		FakeBackend.Delivery toLobby = lobby.delivery();
		assertEquals(2, toLobby.targets().size());
		assertTrue(toLobby.targets().containsAll(List.of("a", "b")));
		assertEquals(List.of("c"), survival.delivery().targets());
	}

	@Test
	void refusesRemoteCommandsUnlessTheyAreTurnedOn() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		lobby.consoleCommand(List.of("survival"), "op eult");
		lobby.ping(1);

		assertTrue(log.sawWarning("'remote-commands' is off"));
		assertEquals(1, survival.ping(1));
	}

	@Test
	void sendsARemoteCommandWhenTheyAreTurnedOn() throws IOException {
		server.stop();
		server = start(true, true);
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		lobby.consoleCommand(List.of("survival"), "save-all");

		assertEquals("save-all", survival.awaitConsoleCommand());
	}

	@Test
	void sendsARemoteCommandEverywhereWhenNoServerIsNamed() throws IOException {
		server.stop();
		server = start(true, true);
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		lobby.consoleCommand(List.of(), "save-all");

		assertEquals("save-all", lobby.awaitConsoleCommand());
		assertEquals("save-all", survival.awaitConsoleCommand());
	}

	@Test
	void keepsQuietAboutPlayersWhenTheFeatureIsOff() throws IOException {
		server.stop();
		server = start(false, false);
		FakeBackend lobby = synced("lobby");

		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));
		lobby.playerAction(PlayerAction.MESSAGE, List.of("eult"), "{\"text\":\"hi\"}");

		assertEquals(3, lobby.ping(3));
		assertFalse(server.playersEnabled());
	}

	@Test
	void reportsWhichFeaturesAreOn() throws IOException {
		assertTrue(server.playersEnabled());
		assertFalse(server.remoteCommandsEnabled());

		server.stop();
		server = start(true, true);

		assertTrue(server.remoteCommandsEnabled());
	}

	@Test
	void connectGoesToTheProxyRatherThanABackend() throws IOException {
		List<String> moved = new ArrayList<>();
		server.actions((player, target) -> moved.add(player + " -> " + target));

		FakeBackend lobby = synced("lobby");
		lobby.playerAction(PlayerAction.CONNECT, List.of("eult"), "survival");
		lobby.ping(1);

		await(() -> moved.size() == 1, "connect never reached the proxy");
		assertEquals("eult -> survival", moved.get(0));
	}

	@Test
	void doesNothingWhenNoPlatformCanConnect() throws IOException {
		FakeBackend lobby = synced("lobby");

		lobby.playerAction(PlayerAction.CONNECT, List.of("eult"), "survival");

		assertEquals(1, lobby.ping(1));
	}

	@Test
	void hasNothingToSayAboutAnUnknownServer() {
		NetworkState state = new NetworkState();

		assertNull(state.serverOf("nobody"));
		assertFalse(state.holds("lobby"));
		assertTrue(state.names().isEmpty());
	}

	@Test
	void aRejectedHandshakeDoesNotEvictTheServerAlreadyUsingThatName() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));
		awaitState(1);

		// a copy-pasted config on another box: same server-name, stale token. it retries
		// every few seconds, and each refusal must not touch the real lobby's entry
		FakeBackend impostor = new FakeBackend("lobby", port, "wrong-token", Protocol.VERSION, 0, 0);
		backends.add(impostor);
		impostor.rejection();
		impostor.close();

		for (List<RemoteServer> state : survival.statesUntilQuiet(500))
			assertTrue(state.stream().anyMatch(each -> each.name().equals("lobby")),
					"the rejected connection evicted the real lobby: " + names(state));

		List<RemoteServer> handed = synced("lobby2").networkState();
		assertTrue(handed.stream().anyMatch(each -> each.name().equals("lobby")),
				"a new backend was told lobby is gone: " + names(handed));
	}

	@Test
	void aStaleConnectionClosingDoesNotEvictTheBackendThatReplacedIt() throws IOException {
		// the proxy has not noticed this socket is dead: a hard reboot sends no FIN
		FakeBackend stale = synced("lobby");
		// the same server came back up before the proxy found out
		FakeBackend fresh = synced("lobby");
		FakeBackend survival = synced("survival");
		fresh.sendServerInfo(new RemoteServer("lobby", "m", "v", 20, List.of("eult"), List.of()));
		awaitState(1);

		stale.close();

		for (List<RemoteServer> state : survival.statesUntilQuiet(500))
			assertTrue(state.stream().anyMatch(each -> each.name().equals("lobby")),
					"the dead socket took the live lobby with it: " + names(state));

		survival.playerAction(PlayerAction.MESSAGE, List.of("eult"), "{\"text\":\"hi\"}");
		assertEquals(List.of("eult"), fresh.delivery().targets());
	}

	@Test
	void leavesEveryBackendHoldingTheLatestStateWhenReportsRace() throws Exception {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		List<FakeBackend> watchers = new ArrayList<>();
		for (int i = 0; i < 12; i++)
			watchers.add(synced("watcher" + i));

		int rounds = 300;
		// a player hopping between two servers makes both report at the same instant
		Thread fromLobby = new Thread(() -> report(lobby, "lobby", rounds));
		Thread fromSurvival = new Thread(() -> report(survival, "survival", rounds));
		// and a third one restarting adds and removes an entry while the others report
		Thread churn = new Thread(() -> {
			try {
				for (int i = 0; i < 40; i++) {
					FakeBackend hopper = new FakeBackend("hopper", port, TOKEN);
					hopper.sync();
					hopper.sendServerInfo(new RemoteServer("hopper", "m", "v", 1, List.of(), List.of()));
					hopper.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		fromLobby.start();
		fromSurvival.start();
		churn.start();
		fromLobby.join();
		fromSurvival.join();
		churn.join();

		String last = String.valueOf(rounds - 1);
		for (FakeBackend watcher : watchers) {
			List<List<RemoteServer>> states = watcher.statesUntilQuiet(500);
			assertFalse(states.isEmpty(), watcher.name() + " heard nothing");
			List<RemoteServer> held = states.get(states.size() - 1);
			for (RemoteServer server : held) {
				if (server.name().equals("lobby") || server.name().equals("survival"))
					assertEquals(List.of(last), server.players(), watcher.name() + " was left holding "
							+ "an older report for " + server.name() + " after " + states.size() + " frames");
			}
			assertEquals(2, held.stream().filter(s -> !s.name().equals("hopper")).count(),
					watcher.name() + " ended with " + names(held));
		}
	}

	private static void report(FakeBackend backend, String name, int rounds) {
		try {
			for (int i = 0; i < rounds; i++)
				backend.sendServerInfo(new RemoteServer(name, "m", "v", 20,
						List.of(String.valueOf(i)), List.of()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> names(List<RemoteServer> state) {
		return state.stream().map(RemoteServer::name).toList();
	}

	private void awaitState(int servers) throws IOException {
		for (FakeBackend backend : backends) {
			List<RemoteServer> state = backend.networkState();
			while (state.size() < servers)
				state = backend.networkState();
		}
	}

	private NetworkServer start(boolean players, boolean remoteCommands) throws IOException {
		BindException lost = null;

		for (int attempt = 0; attempt < 20; attempt++) {
			int candidate = freePort();
			NetworkServer started = new NetworkServer("127.0.0.1", candidate, TOKEN, null, 10, 2.0,
					10_000, log);
			try {
				started.start();
				started.features(players, remoteCommands);
				port = candidate;
				return started;
			} catch (BindException taken) {
				lost = taken;
				started.stop();
			}
		}
		throw lost;
	}

	private FakeBackend synced(String name) throws IOException {
		FakeBackend backend = new FakeBackend(name, port, TOKEN, Protocol.VERSION, 0, 0);
		backends.add(backend);
		backend.sync();
		return backend;
	}

	private static void await(BooleanSupplier condition, String message) {
		long deadline = System.currentTimeMillis() + FakeBackend.TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean())
				return;
			Thread.onSpinWait();
		}
		fail(message);
	}

	private static int freePort() throws IOException {
		ServerSocket probe = new ServerSocket();
		try (probe) {
			probe.setReuseAddress(true);
			probe.bind(new InetSocketAddress("127.0.0.1", 0));
			return probe.getLocalPort();
		}
	}
}
