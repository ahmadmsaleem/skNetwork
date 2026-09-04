package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sknetwork.common.Protocol;

class ProxySettingsTest {

	@Test
	void fallsBackToTheShippedDefaults() {
		ProxySettings settings = ProxySettings.from(new MapConfig());

		assertEquals("127.0.0.1", settings.bind());
		assertEquals(Protocol.DEFAULT_PORT, settings.port());
		assertEquals("change-me", settings.token());
		assertFalse(settings.debug());
		assertEquals("network.csv", settings.logName());
		assertEquals(100, settings.flushIntervalMs());
		assertEquals(2.0, settings.compactRatio());
		assertEquals(10_000, settings.replayBuffer());
		assertFalse(settings.scriptsEnabled());
		assertEquals(512 * 1024L, settings.maxFileBytes());
		assertEquals(16 * 1024 * 1024L, settings.maxTotalBytes());
	}

	@Test
	void readsWhatTheFileSays() {
		ProxySettings settings = ProxySettings.from(new MapConfig()
				.set("bind", "0.0.0.0")
				.set("port", 25_600)
				.set("token", "a real secret")
				.set("debug", true)
				.set("log", "changes.csv")
				.set("flush-interval", "2s")
				.set("compact-when", 4.5)
				.set("replay-buffer", 50)
				.set("scripts.enabled", true)
				.set("scripts.max-file-kb", 64)
				.set("scripts.max-total-mb", 1));

		assertEquals("0.0.0.0", settings.bind());
		assertEquals(25_600, settings.port());
		assertEquals("a real secret", settings.token());
		assertTrue(settings.debug());
		assertEquals("changes.csv", settings.logName());
		assertEquals(2_000, settings.flushIntervalMs());
		assertEquals(4.5, settings.compactRatio());
		assertEquals(50, settings.replayBuffer());
		assertTrue(settings.scriptsEnabled());
		assertEquals(64 * 1024L, settings.maxFileBytes());
		assertEquals(1024 * 1024L, settings.maxTotalBytes());
	}

	@Test
	void collectsTheGroupsInFileOrder() {
		ProxySettings settings = ProxySettings.from(new MapConfig()
				.list("scripts.groups", "hubs", List.of("lobby", "lobby2"))
				.list("scripts.groups", "minigames", List.of("bedwars1")));

		assertEquals(List.of("hubs", "minigames"), List.copyOf(settings.groups().keySet()));
		assertEquals(List.of("lobby", "lobby2"), settings.groups().get("hubs"));
	}

	@Test
	void persistsByDefault() {
		assertTrue(ProxySettings.from(new MapConfig()).persists());
	}

	@Test
	void keepsNothingWhenTheLogIsTurnedOff() {
		assertFalse(ProxySettings.from(new MapConfig().set("log", "none")).persists());
		assertFalse(ProxySettings.from(new MapConfig().set("log", "NONE")).persists());
		assertFalse(ProxySettings.from(new MapConfig().set("log", "  ")).persists());
	}

	@Test
	void warnsOnlyWhenTheDefaultTokenIsReachable() {
		assertFalse(ProxySettings.from(new MapConfig()).tokenIsExposedDefault());
		assertFalse(ProxySettings.from(new MapConfig().set("token", "a real secret")
				.set("bind", "0.0.0.0")).tokenIsExposedDefault());
		assertTrue(ProxySettings.from(new MapConfig().set("bind", "0.0.0.0")).tokenIsExposedDefault());
	}
}
