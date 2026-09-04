package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StyleTest {

	private static final String SECTION = "§";

	@Test
	void spellsAHexColourTheWayLegacyChatDoes() {
		assertEquals(SECTION + "x" + SECTION + "B" + SECTION + "0" + SECTION + "D"
				+ SECTION + "D" + SECTION + "4" + SECTION + "A", Style.hex("B0DD4A"));
		assertEquals(Style.hex(Style.BRAND_HEX), Style.BRAND);
	}

	@Test
	void swapsHexForAPlainColourForTheConsole() {
		String line = Style.BRAND + "ready" + Style.RESET;

		assertEquals(Style.GOOD + "ready" + Style.RESET, Style.downgrade(line));
		assertFalse(Style.downgrade(line).contains(SECTION + "x"));
	}

	@Test
	void leavesAPlainLineAlone() {
		String line = Style.LABEL + "state" + Style.VALUE + "ready";

		assertEquals(line, Style.downgrade(line));
	}

	@Test
	void downgradesEveryHexOnTheLine() {
		String line = Style.hex("FF0000") + "a" + Style.hex("00FF00") + "b";

		assertFalse(Style.downgrade(line).contains(SECTION + "x"));
	}

	@Test
	void padsLabelsToOneWidth() {
		String shortLabel = Style.row("state", "ready");
		String longLabel = Style.row("variables", "12");

		assertEquals(shortLabel.indexOf("ready"), longLabel.indexOf("12"));
	}

	@Test
	void keepsALabelThatIsAlreadyTooLong() {
		assertTrue(Style.row("an extremely long label", "x").contains("an extremely long label "));
	}

	@Test
	void separatesThousands() {
		assertEquals("1,234,567", Style.number(1_234_567));
		assertEquals("0", Style.number(0));
		assertEquals("-1,000", Style.number(-1000));
	}

	@Test
	void namesTheAddonAndTheProtocolInTheHeader() {
		String header = Style.header("0.0.1", Protocol.VERSION);

		assertTrue(header.contains("sk"));
		assertTrue(header.contains("Network"));
		assertTrue(header.contains("0.0.1"));
		assertTrue(header.contains("protocol " + Style.VALUE + Protocol.VERSION));
	}

	@Test
	void hangsEveryRowOffTheSameEdge() {
		String edge = Style.MUTED + "│";

		assertTrue(Style.row("a", "b").startsWith(edge));
		assertTrue(Style.rowRaw("a", "b").startsWith(edge));
		assertTrue(Style.hint("/sknet", "state").startsWith(edge));
		assertTrue(Style.note("anything").startsWith(edge));
		assertEquals(edge, Style.gap());
	}
}
