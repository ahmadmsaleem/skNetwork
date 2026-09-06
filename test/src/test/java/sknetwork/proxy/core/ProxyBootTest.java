package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyBootTest {

	@TempDir
	File dataFolder;

	private RecordingLog log;
	private NetworkServer server;

	@BeforeEach
	void setUp() {
		log = new RecordingLog();
	}

	@AfterEach
	void tearDown() {
		if (server != null)
			server.stop();
	}

	/**
	 * A fresh install has distribution off, so this is the only state most people ever
	 * see. An empty scripts/ folder beside config.yml is how they find out it exists.
	 */
	@Test
	void makesTheScriptsFolderEvenWhenDistributionIsOff() throws IOException {
		server = boot(false);

		File scripts = new File(dataFolder, "scripts");

		assertTrue(scripts.isDirectory(), "scripts/ should be created on a default install");
		assertTrue(new File(scripts, "global").isDirectory());
		assertTrue(new File(scripts, "README.txt").isFile());
		assertTrue(log.sawAny("script distribution is off"));
	}

	@Test
	void theReadmeSaysHowToTurnItOn() throws IOException {
		server = boot(false);

		String readme = Files.readString(new File(dataFolder, "scripts/README.txt").toPath(),
				StandardCharsets.UTF_8);

		assertTrue(readme.contains("scripts.enabled"));
		assertTrue(readme.contains("/sknetproxy push"));
	}

	@Test
	void makesTheSameFolderWhenDistributionIsOn() throws IOException {
		server = boot(true);

		assertTrue(new File(dataFolder, "scripts/global").isDirectory());
		assertTrue(log.sawAny("script distribution is on"));
	}

	/** Somebody's own notes in there are theirs, not ours to replace on every boot. */
	@Test
	void leavesAnExistingFolderAlone() throws IOException {
		File scripts = new File(dataFolder, "scripts");
		assertTrue(new File(scripts, "global").mkdirs());
		Files.writeString(new File(scripts, "README.txt").toPath(), "mine", StandardCharsets.UTF_8);

		server = boot(false);

		assertEquals("mine", Files.readString(new File(scripts, "README.txt").toPath(),
				StandardCharsets.UTF_8));
	}

	private NetworkServer boot(boolean scriptsEnabled) throws IOException {
		MapConfig config = new MapConfig()
				.set("bind", "127.0.0.1")
				.set("port", freePort())
				.set("token", "a-test-token")
				.set("log", "none")
				.set("scripts.enabled", scriptsEnabled);
		return ProxyBoot.start(ProxySettings.from(config), dataFolder, log);
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
