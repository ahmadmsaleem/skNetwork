package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class PingSettingsTest {

	@Test
	void survivesTheWireWithEveryFieldSet() throws IOException {
		PingSettings sent = new PingSettings("§aMy Network", "500", "42");

		PacketOut out = PacketOut.body();
		sent.write(out);

		assertEquals(sent, PingSettings.read(new PacketIn(out.payload())));
	}

	@Test
	void keepsUnsetFieldsUnset() throws IOException {
		PacketOut out = PacketOut.body();
		new PingSettings("§amotd only", null, null).write(out);

		PingSettings read = PingSettings.read(new PacketIn(out.payload()));

		assertEquals("§amotd only", read.motd());
		assertNull(read.maxPlayers());
		assertNull(read.playerCount());
		assertTrue(PingSettings.NONE.isEmpty());
	}

	/** A script beats config.yml, and config.yml fills in whatever the script left alone. */
	@Test
	void aScriptValueWinsAndTheConfigFillsTheRest() {
		PingSettings script = new PingSettings(null, "500", null);
		PingSettings config = new PingSettings("§afrom config", "20", "0");

		PingSettings effective = script.over(config);

		assertEquals("§afrom config", effective.motd());
		assertEquals("500", effective.maxPlayers());
		assertEquals("0", effective.playerCount());
	}

	@Test
	void readsNumbersAndRefusesRubbish() {
		PingSettings ping = new PingSettings(null, "500", "not a number");

		assertEquals(500, ping.number(PingField.MAX_PLAYERS));
		assertNull(ping.number(PingField.PLAYER_COUNT));
		assertNull(PingSettings.NONE.number(PingField.MAX_PLAYERS));
	}

	@Test
	void setsAndClearsOneFieldAtATime() {
		PingSettings ping = PingSettings.NONE.with(PingField.MOTD, "§ahello");

		assertEquals("§ahello", ping.get(PingField.MOTD));
		assertTrue(ping.with(PingField.MOTD, null).isEmpty());
		assertEquals(2, PingField.PLAYER_COUNT.ordinal());
	}
}
