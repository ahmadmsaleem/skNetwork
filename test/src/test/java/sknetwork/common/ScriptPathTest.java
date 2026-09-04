package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ScriptPathTest {

	@ParameterizedTest
	@ValueSource(strings = {"global/coins.sk", "hubs/nested/deep/thing.sk", "a.sk"})
	void acceptsAPlainScript(String path) {
		assertTrue(ScriptPath.isSafe(path));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"../server.properties",
			"../../../etc/passwd.sk",
			"global/../../../server.properties.sk",
			"global/../secrets.sk",
			"./global/coins.sk",
			"global/./coins.sk"})
	void refusesToEscapeTheScriptsFolder(String path) {
		assertFalse(ScriptPath.isSafe(path));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/etc/cron.sk", "\\windows\\system32.sk", "C:/plugins/evil.sk",
			"global\\coins.sk"})
	void refusesAnAbsoluteOrWindowsPath(String path) {
		assertFalse(ScriptPath.isSafe(path));
	}

	@ParameterizedTest
	@ValueSource(strings = {"global/coins.yml", "global/coins", "global/coins.sk.txt", "global/.sk.bak"})
	void refusesAnythingThatIsNotAScript(String path) {
		assertFalse(ScriptPath.isSafe(path));
	}

	@Test
	void refusesADisabledScript() {
		assertFalse(ScriptPath.isSafe("-coins.sk"));
		assertFalse(ScriptPath.isSafe("global/-coins.sk"));
		assertFalse(ScriptPath.isSafe("-global/coins.sk"));
	}

	@Test
	void refusesAnEmptySegment() {
		assertFalse(ScriptPath.isSafe("global//coins.sk"));
		assertFalse(ScriptPath.isSafe("/coins.sk"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t"})
	void refusesNothingAtAll(String path) {
		assertFalse(ScriptPath.isSafe(path));
	}

	@Test
	void refusesAPathLongerThanTheLimit() {
		assertTrue(ScriptPath.isSafe("g/" + "a".repeat(195) + ".sk"));
		assertFalse(ScriptPath.isSafe("g/" + "a".repeat(500) + ".sk"));
	}
}
