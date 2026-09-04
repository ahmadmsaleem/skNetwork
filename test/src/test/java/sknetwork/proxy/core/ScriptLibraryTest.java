package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sknetwork.common.Manifest;

class ScriptLibraryTest {

	private static final long MAX_FILE = 512 * 1024L;
	private static final long MAX_TOTAL = 16 * 1024 * 1024L;

	@TempDir
	File dataFolder;

	private RecordingLog log;

	@BeforeEach
	void setUp() {
		log = new RecordingLog();
	}

	@Test
	void findsNothingInAnEmptyFolder() {
		ScriptLibrary library = library();

		assertFalse(library.rescan());
		assertEquals(0, library.fileCount());
		assertEquals(0, library.version());
	}

	@Test
	void picksUpAScriptInAGroupFolder() throws IOException {
		script("global/coins.sk", "on load:");
		ScriptLibrary library = library();

		assertTrue(library.rescan());
		assertEquals(1, library.fileCount());
		assertEquals(1, library.version());
	}

	@Test
	void bumpsTheVersionOnlyWhenSomethingChanged() throws IOException {
		script("global/coins.sk", "on load:");
		ScriptLibrary library = library();

		library.rescan();
		assertFalse(library.rescan());
		assertEquals(1, library.version());

		script("global/coins.sk", "on load:\n\tbroadcast \"hi\"");
		assertTrue(library.rescan());
		assertEquals(2, library.version());
	}

	@Test
	void noticesAScriptGoingAway() throws IOException {
		script("global/coins.sk", "on load:");
		ScriptLibrary library = library();
		library.rescan();

		assertTrue(new File(dataFolder, "scripts/global/coins.sk").delete());

		assertTrue(library.rescan());
		assertEquals(0, library.fileCount());
	}

	@Test
	void remembersTheVersionAcrossARestart() throws IOException {
		script("global/coins.sk", "on load:");
		library().rescan();

		assertEquals(1, library().version());
	}

	@Test
	void handsBackTheBytesForAFetch() throws IOException {
		script("global/coins.sk", "on load:\n\tbroadcast \"hi\"");
		ScriptLibrary library = library();
		library.rescan();

		assertArrayEquals("on load:\n\tbroadcast \"hi\"".getBytes(StandardCharsets.UTF_8),
				library.content("global/coins.sk"));
		assertNull(library.content("global/missing.sk"));
	}

	@Test
	void sendsGlobalScriptsToEveryServer() throws IOException {
		script("global/coins.sk", "on load:");
		ScriptLibrary library = library();
		library.rescan();

		assertEquals(1, library.manifestFor("lobby").size());
		assertEquals(1, library.manifestFor("a server nobody configured").size());
	}

	@Test
	void sendsAGroupScriptOnlyToItsMembers() throws IOException {
		script("global/everywhere.sk", "on load:");
		script("hubs/lobby-only.sk", "on load:");
		ScriptLibrary library = library();
		library.groups(groups("hubs", List.of("lobby", "lobby2")));
		library.rescan();

		assertEquals(2, library.manifestFor("lobby").size());
		assertEquals(2, library.manifestFor("lobby2").size());
		assertEquals(1, library.manifestFor("survival").size());
		assertTrue(library.manifestFor("survival").hashesByPath().containsKey("global/everywhere.sk"));
	}

	@Test
	void stampsEveryManifestWithTheSameVersion() throws IOException {
		script("global/a.sk", "on load:");
		ScriptLibrary library = library();
		library.rescan();

		assertEquals(library.version(), library.manifestFor("lobby").version());
	}

	@Test
	void hashesWhatIsOnDisk() throws IOException {
		script("global/a.sk", "on load:");
		ScriptLibrary library = library();
		library.rescan();

		assertEquals(Manifest.hash("on load:"),
				library.manifestFor("lobby").hashesByPath().get("global/a.sk"));
	}

	@Test
	void skipsAScriptLooseInTheScriptsFolder() throws IOException {
		script("loose.sk", "on load:");
		ScriptLibrary library = library();

		assertFalse(library.rescan());
		assertEquals(0, library.fileCount());
		assertTrue(log.sawWarning("scripts go in a group folder"));
	}

	@Test
	void skipsADisabledScript() throws IOException {
		script("global/-off.sk", "on load:");
		ScriptLibrary library = library();

		assertFalse(library.rescan());
		assertTrue(log.sawWarning("not a name we will push"));
	}

	@Test
	void skipsAnythingThatIsNotAScript() throws IOException {
		script("global/notes.txt", "just notes");
		ScriptLibrary library = library();

		assertFalse(library.rescan());
		assertEquals(0, library.fileCount());
	}

	@Test
	void skipsAFileOverTheSizeLimit() throws IOException {
		script("global/big.sk", "x".repeat(2048));
		script("global/small.sk", "on load:");
		ScriptLibrary library = new ScriptLibrary(dataFolder, log, 1024, MAX_TOTAL);

		assertTrue(library.rescan());
		assertEquals(1, library.fileCount());
		assertTrue(log.sawWarning("over the 1024 byte limit"));
	}

	@Test
	void stopsScanningOnceTheLibraryIsTooBig() throws IOException {
		for (int i = 0; i < 10; i++)
			script("global/" + i + ".sk", "x".repeat(200));
		ScriptLibrary library = new ScriptLibrary(dataFolder, log, MAX_FILE, 500);

		library.rescan();

		assertTrue(library.fileCount() < 10);
		assertTrue(log.sawWarning("over the 500 byte limit"));
	}

	@Test
	void warnsAboutAFolderNoGroupCovers() throws IOException {
		script("minigames/bedwars.sk", "on load:");
		ScriptLibrary library = library();
		library.groups(groups("hubs", List.of("lobby")));

		library.rescan();

		assertTrue(log.sawWarning("scripts/minigames/ has no matching group"));
		assertEquals(0, library.manifestFor("lobby").size());
	}

	@Test
	void createsTheFolderAndANote() {
		ScriptLibrary library = library();

		library.ensureFolder();

		assertTrue(new File(dataFolder, "scripts/global").isDirectory());
		assertTrue(new File(dataFolder, "scripts/README.txt").isFile());
	}

	@Test
	void leavesAnExistingFolderAlone() throws IOException {
		script("global/a.sk", "on load:");
		ScriptLibrary library = library();

		library.ensureFolder();

		assertFalse(new File(dataFolder, "scripts/README.txt").isFile());
	}

	private ScriptLibrary library() {
		return new ScriptLibrary(dataFolder, log, MAX_FILE, MAX_TOTAL);
	}

	private void script(String path, String content) throws IOException {
		File file = new File(new File(dataFolder, "scripts"), path);
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
	}

	private static ServerGroups groups(String name, List<String> members) {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put(name, members);
		return new ServerGroups(configured);
	}
}
