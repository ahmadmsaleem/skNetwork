package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import sknetwork.common.RemoteServer;

class ConfigReloadTest {

	private static final String TOKEN = "a-test-token";

	@TempDir
	File dataFolder;

	private RecordingLog log;
	private NetworkServer server;
	private int port;

	private MapConfig onDisk;
	private IOException unreadable;

	private final List<FakeBackend> backends = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		log = new RecordingLog();
		port = freePort();
	}

	@AfterEach
	void tearDown() {
		backends.forEach(FakeBackend::close);
		backends.clear();
		if (server != null)
			server.stop();
	}

	@Test
	void saysNothingChangedWhenTheFileMatchesWhatIsRunning() throws IOException {
		boot(config());

		ConfigReload.Report report = server.reload();

		assertFalse(report.failed());
		assertTrue(report.nothingChanged());
		assertTrue(report.applied().isEmpty());
	}

	@Test
	void turnsRemoteCommandsOnWithoutARestart() throws IOException {
		boot(config());
		assertFalse(server.remoteCommandsEnabled());

		onDisk = config().set("remote-commands", true);
		ConfigReload.Report report = server.reload();

		assertTrue(server.remoteCommandsEnabled());
		assertTrue(report.applied().contains("remote-commands"));
		assertTrue(report.restartNeeded().isEmpty());
	}

	@Test
	void opensTheDebugGateWithoutARestart() throws IOException {
		boot(config());
		FakeBackend lobby = synced("lobby");

		lobby.set("before", "long", eight(1));
		await(() -> server.variableCount() == 1, "the first write never landed");
		assertFalse(log.sawAny("DEBUG"), "debug is off at boot, so nothing should be logged");

		onDisk = config().set("debug", true);
		assertTrue(server.reload().applied().contains("debug"));

		lobby.set("after", "long", eight(2));
		await(() -> log.sawAny("DEBUG"), "no debug line after the reload turned it on");
	}

	@Test
	void turningPlayersOffTellsEveryBackendToEmptyItsList() throws IOException {
		boot(config());
		FakeBackend lobby = synced("lobby");
		assertTrue(lobby.networkState().isEmpty(), "the state that comes with the sync");

		lobby.sendServerInfo(new RemoteServer("lobby", "a motd", "1.21", 20,
				List.of("Notch"), List.of()));
		assertEquals(1, lobby.networkState().size());

		onDisk = config().set("players", false);
		ConfigReload.Report report = server.reload();

		assertTrue(report.applied().contains("players"));
		assertTrue(lobby.networkState().isEmpty(), "the backend should have been emptied");
	}

	@Test
	void namesTheKeysThatNeedARestartAndLeavesThemAlone() throws IOException {
		boot(config());

		onDisk = config().set("port", port + 1).set("token", "a-different-token")
				.set("replay-buffer", 50);
		ConfigReload.Report report = server.reload();

		assertEquals(List.of("port", "token", "replay-buffer"), report.restartNeeded());
		assertEquals(TOKEN, server.token(), "the running token must not move under live backends");
	}

	@Test
	void keepsReportingARestartKeyUntilTheProxyActuallyRestarts() throws IOException {
		boot(config());
		onDisk = config().set("port", port + 1);

		assertEquals(List.of("port"), server.reload().restartNeeded());
		assertEquals(List.of("port"), server.reload().restartNeeded());
	}

	@Test
	void aConfigThatWillNotReadChangesNothing() throws IOException {
		boot(config());
		onDisk = config().set("remote-commands", true);
		unreadable = new IOException("config.yml is not valid YAML");

		ConfigReload.Report report = server.reload();

		assertTrue(report.failed());
		assertTrue(report.error().contains("not valid YAML"));
		assertFalse(server.remoteCommandsEnabled(), "a broken file must not apply half of itself");
	}

	@Test
	void regroupingPushesAManifestThatDropsTheScript() throws IOException {
		script("hubs/welcome.sk", "on load:");
		boot(config().list("scripts.groups", "hubs", List.of("lobby")));

		FakeBackend lobby = synced("lobby");
		Manifest first = lobby.manifest();
		assertEquals(List.of("hubs/welcome.sk"), List.copyOf(first.hashesByPath().keySet()));

		onDisk = config().list("scripts.groups", "hubs", List.of("survival"));
		ConfigReload.Report report = server.reload();

		assertTrue(report.applied().contains("groups"));
		Manifest second = lobby.manifest();
		assertTrue(second.hashesByPath().isEmpty(), "lobby is no longer in hubs");
		assertTrue(second.version() > first.version(), "the version has to move for a regroup");
	}

	@Test
	void regroupingPushesAManifestThatAddsTheScript() throws IOException {
		script("hubs/welcome.sk", "on load:");
		boot(config().list("scripts.groups", "hubs", List.of("survival")));

		FakeBackend lobby = synced("lobby");
		assertTrue(lobby.manifest().hashesByPath().isEmpty());

		onDisk = config().list("scripts.groups", "hubs", List.of("lobby", "survival"));
		server.reload();

		assertEquals(List.of("hubs/welcome.sk"),
				List.copyOf(lobby.manifest().hashesByPath().keySet()));
	}

	@Test
	void aBackendThatConnectsLaterGetsTheRegroupedManifest() throws IOException {
		script("hubs/welcome.sk", "on load:");
		boot(config().list("scripts.groups", "hubs", List.of("survival")));

		onDisk = config().list("scripts.groups", "hubs", List.of("lobby"));
		server.reload();

		FakeBackend lobby = synced("lobby");
		assertEquals(List.of("hubs/welcome.sk"),
				List.copyOf(lobby.manifest().hashesByPath().keySet()));
	}

	@Test
	void raisingTheFileLimitPicksUpAScriptThatWasBeingSkipped() throws IOException {
		script("global/big.sk", "x".repeat(2_000));
		boot(config().set("scripts.max-file-kb", 1));

		FakeBackend lobby = synced("lobby");
		assertTrue(lobby.manifest().hashesByPath().isEmpty(), "2000 bytes is over a 1kB limit");

		onDisk = config().set("scripts.max-file-kb", 8);
		ConfigReload.Report report = server.reload();

		assertTrue(report.applied().contains("max-file-kb"));
		assertEquals(List.of("global/big.sk"),
				List.copyOf(lobby.manifest().hashesByPath().keySet()));
	}

	@Test
	void saysSoWhenScriptDistributionIsOff() throws IOException {
		boot(config().set("scripts.enabled", false));

		onDisk = config().set("scripts.enabled", false).list("scripts.groups", "hubs", List.of("lobby"));
		ConfigReload.Report report = server.reload();

		assertFalse(report.applied().contains("groups"));
		assertTrue(report.notes().stream().anyMatch(note -> note.contains("script distribution is off")));
	}

	@Test
	void addingAPatternTakesTheValueOffTheDiskButLeavesItLive() throws IOException {
		boot(config().set("log", "network.csv"));
		FakeBackend lobby = synced("lobby");
		lobby.set("session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8));
		await(() -> server.variableCount() == 1, "the write never landed");

		onDisk = config().set("log", "network.csv").set("no-persist", List.of("session::*"));
		ConfigReload.Report report = server.reload();

		assertTrue(report.applied().contains("no-persist"));
		assertFalse(logFile().contains("session::token"), "the value should be off the disk");
		assertFalse(backupFile().contains("session::token"), "and out of the backup with it");
		assertEquals(1, server.variableCount(), "no-persist is about the disk, not about memory");
	}

	@Test
	void aLaterWriteToAMatchingNameNeverReachesTheDisk() throws IOException {
		boot(config().set("log", "network.csv"));
		FakeBackend lobby = synced("lobby");

		onDisk = config().set("log", "network.csv").set("no-persist", List.of("session::*"));
		server.reload();

		lobby.set("session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8));
		await(() -> server.variableCount() == 1, "the write never landed");
		server.compactNow();

		assertFalse(logFile().contains("session::token"));
	}

	/** The mirror image: dropping a pattern has to put those values back on the disk. */
	@Test
	void removingAPatternPutsTheValueBackOnTheDisk() throws IOException {
		boot(config().set("log", "network.csv").set("no-persist", List.of("session::*")));
		FakeBackend lobby = synced("lobby");
		lobby.set("session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8));
		await(() -> server.variableCount() == 1, "the write never landed");
		assertFalse(logFile().contains("session::token"), "held back while the pattern was set");

		onDisk = config().set("log", "network.csv");
		assertTrue(server.reload().applied().contains("no-persist"));

		assertTrue(logFile().contains("session::token"), "it should be persisted again now");
	}

	@Test
	void saysNothingWasOnTheDiskAnywayWhenTheLogIsOff() throws IOException {
		boot(config());

		onDisk = config().set("no-persist", List.of("session::*"));
		ConfigReload.Report report = server.reload();

		assertTrue(report.applied().contains("no-persist"));
		assertTrue(report.notes().stream().anyMatch(note -> note.contains("'log' is none")));
	}

	// --- helpers ------------------------------------------------------------

	private MapConfig config() {
		return new MapConfig()
				.set("bind", "127.0.0.1")
				.set("port", port)
				.set("token", TOKEN)
				.set("log", "none");
	}

	private void boot(MapConfig config) throws IOException {
		onDisk = config;
		server = ProxyBoot.start(ProxySettings.from(config), dataFolder, log);
		server.reloader(() -> {
			if (unreadable != null)
				throw unreadable;
			return ProxySettings.from(onDisk);
		});
	}

	private void script(String path, String content) throws IOException {
		File file = new File(new File(dataFolder, "scripts"), path);
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
	}

	private String logFile() throws IOException {
		return Files.readString(new File(dataFolder, "network.csv").toPath(), StandardCharsets.UTF_8);
	}

	private String backupFile() throws IOException {
		File backup = new File(dataFolder, "network.csv.bak");
		return backup.isFile()
				? Files.readString(backup.toPath(), StandardCharsets.UTF_8)
				: "";
	}

	private static byte[] eight(long value) {
		byte[] out = new byte[8];
		for (int i = 7; i >= 0; i--) {
			out[i] = (byte) (value & 0xFF);
			value >>= 8;
		}
		return out;
	}

	private FakeBackend synced(String name) throws IOException {
		FakeBackend backend = new FakeBackend(name, port, TOKEN);
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
