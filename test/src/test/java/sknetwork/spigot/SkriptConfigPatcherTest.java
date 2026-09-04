package sknetwork.spigot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkriptConfigPatcherTest {

	private static final String SKRIPT_CONFIG = """
			version: 2.16.2

			language: english

			databases:

				# Disabled example
				MySQL:
					pattern: .*
					type: disabled
					host: localhost

				# The default, a CSV file on this server's disk
				default:
					pattern: .*
					type: CSV
					file: ./plugins/Skript/variables.csv
					backup interval: 2 hours

			verbosity: normal
			""";

	@TempDir
	File folder;

	@Test
	void saysSoWhenThereIsNoConfigYet() {
		SkriptConfigPatcher patcher = new SkriptConfigPatcher(new File(folder, "config.sk"), "?");

		assertEquals(SkriptConfigPatcher.Result.NO_CONFIG_FILE, patcher.patch());
		assertTrue(patcher.detail().contains("does not exist"));
	}

	@Test
	void saysSoWhenThereIsNoDatabasesSection() throws IOException {
		File config = write("version: 2.16.2\n\nlanguage: english\n");
		SkriptConfigPatcher patcher = new SkriptConfigPatcher(config, "?");

		assertEquals(SkriptConfigPatcher.Result.NO_DATABASES_SECTION, patcher.patch());
		assertTrue(patcher.detail().contains("no 'databases:' section"));
	}

	@Test
	void saysSoWhenTheSectionIsEmpty() throws IOException {
		File config = write("databases:\n\nverbosity: normal\n");
		SkriptConfigPatcher patcher = new SkriptConfigPatcher(config, "?");

		assertEquals(SkriptConfigPatcher.Result.NO_DATABASES_SECTION, patcher.patch());
		assertTrue(patcher.detail().contains("indentation"));
	}

	@Test
	void writesTheBlockIntoTheSection() throws IOException {
		File config = write(SKRIPT_CONFIG);

		assertEquals(SkriptConfigPatcher.Result.PATCHED, new SkriptConfigPatcher(config, "?").patch());

		List<String> lines = read(config);

		assertTrue(lines.contains("\t\ttype: skNetwork"));
		assertTrue(lines.contains("\t\tpattern: [?].*"));
		assertTrue(lines.contains("\tnetwork:"));
	}

	@Test
	void putsTheBlockAboveTheCatchAll() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "?").patch();

		List<String> lines = read(config);

		assertTrue(lines.indexOf("\tnetwork:") < lines.indexOf("\tdefault:"));
	}

	@Test
	void stepsOverADisabledDatabase() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "?").patch();

		List<String> lines = read(config);

		assertTrue(lines.indexOf("\tMySQL:") < lines.indexOf("\tnetwork:"));
	}

	@Test
	void keepsTheCommentsWithTheSectionTheyIntroduce() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "?").patch();

		List<String> lines = read(config);

		assertTrue(lines.indexOf("\tnetwork:")
				< lines.indexOf("\t# The default, a CSV file on this server's disk"));
	}

	@Test
	void leavesTheRestOfTheFileAlone() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "?").patch();

		List<String> lines = read(config);

		assertTrue(lines.contains("version: 2.16.2"));
		assertTrue(lines.contains("verbosity: normal"));
		assertTrue(lines.contains("\t\tfile: ./plugins/Skript/variables.csv"));
		assertTrue(lines.contains("\t\thost: localhost"));
	}

	@Test
	void doesNothingTheSecondTime() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "?").patch();
		List<String> after = read(config);

		SkriptConfigPatcher again = new SkriptConfigPatcher(config, "?");

		assertEquals(SkriptConfigPatcher.Result.ALREADY_PRESENT, again.patch());
		assertTrue(again.detail().contains("already declared on line"));
		assertEquals(after, read(config));
	}

	@Test
	void recognisesTheBlockWhateverTheSpacing() throws IOException {
		File config = write("""
				databases:

					mine:
						type:   SKNETWORK
						pattern: [?].*
				""");

		assertEquals(SkriptConfigPatcher.Result.ALREADY_PRESENT,
				new SkriptConfigPatcher(config, "?").patch());
	}

	@Test
	void picksAnotherNameWhenNetworkIsTaken() throws IOException {
		File config = write("""
				databases:

					network:
						pattern: something::.*
						type: CSV
						file: ./plugins/Skript/network.csv

					default:
						pattern: .*
						type: CSV
						file: ./plugins/Skript/variables.csv
				""");

		assertEquals(SkriptConfigPatcher.Result.PATCHED, new SkriptConfigPatcher(config, "?").patch());
		assertTrue(read(config).contains("\tskNetwork:"));
	}

	@Test
	void appendsToTheEndWhenThereIsNoCatchAll() throws IOException {
		File config = write("""
				databases:

					mine:
						pattern: coins::.*
						type: CSV
						file: ./plugins/Skript/coins.csv

				verbosity: normal
				""");

		assertEquals(SkriptConfigPatcher.Result.PATCHED, new SkriptConfigPatcher(config, "?").patch());

		List<String> lines = read(config);

		assertTrue(lines.indexOf("\tmine:") < lines.indexOf("\tnetwork:"));
		assertTrue(lines.indexOf("\tnetwork:") < lines.indexOf("verbosity: normal"));
	}

	@Test
	void copiesTheIndentationTheFileUses() throws IOException {
		File config = write("""
				databases:

				    default:
				        pattern: .*
				        type: CSV
				""");

		new SkriptConfigPatcher(config, "?").patch();

		assertTrue(read(config).contains("        type: skNetwork"));
	}

	@Test
	void buildsAPatternForASymbolPrefix() {
		assertEquals("[?].*", SkriptConfigPatcher.patternFor("?"));
		assertEquals("[$].*", SkriptConfigPatcher.patternFor("$"));
		assertEquals("[!].*", SkriptConfigPatcher.patternFor("!"));
	}

	@Test
	void buildsAPatternForAWordPrefix() {
		assertEquals("net.*", SkriptConfigPatcher.patternFor("net"));
		assertEquals("net_.*", SkriptConfigPatcher.patternFor("net_"));
		assertEquals("n[-]et.*", SkriptConfigPatcher.patternFor("n-et"));
		assertEquals("[?][?].*", SkriptConfigPatcher.patternFor("??"));
	}

	@Test
	void buildsAPatternThatMatchesWhatItShould() {
		assertTrue("?coins::eult".matches(SkriptConfigPatcher.patternFor("?")));
		assertFalse("coins::eult".matches(SkriptConfigPatcher.patternFor("?")));
		assertTrue("net_coins".matches(SkriptConfigPatcher.patternFor("net_")));
		assertFalse("coins".matches(SkriptConfigPatcher.patternFor("net_")));
	}

	@Test
	void writesThePrefixIntoTheComment() throws IOException {
		File config = write(SKRIPT_CONFIG);
		new SkriptConfigPatcher(config, "net_").patch();

		List<String> lines = read(config);

		assertTrue(lines.stream().anyMatch(line -> line.contains("starting with 'net_'")));
		assertTrue(lines.contains("\t\tpattern: net_.*"));
	}

	private File write(String content) throws IOException {
		File config = new File(folder, "config.sk");
		Files.writeString(config.toPath(), content, StandardCharsets.UTF_8);
		return config;
	}

	private static List<String> read(File config) throws IOException {
		return Files.readAllLines(config.toPath(), StandardCharsets.UTF_8);
	}
}
