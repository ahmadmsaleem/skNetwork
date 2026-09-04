package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvLineTest {

	@Test
	void writesTheShapeTheLogDocuments() {
		assertEquals("1041, coins::a1b2c3, long, 0000000000000064, 100",
				CsvLine.format(1041, "coins::a1b2c3", "long", HexFormat.of().parseHex("0000000000000064"), "100"));
	}

	@Test
	void writesATombstoneForADelete() {
		String line = CsvLine.format(1043, "gone", "long", null, "100");
		String[] fields = CsvLine.split(line);

		assertEquals(5, fields.length);
		assertEquals("gone", fields[1]);
		assertEquals("", fields[2]);
		assertEquals("", fields[3]);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"coins::a1b2c3",
			"party::5::leader",
			"name with spaces",
			"has,a,comma",
			"has \"quotes\" in it",
			"has#a#hash",
			"has\\a\\backslash",
			"line\nbreak",
			"carriage\rreturn",
			"tab\there",
			"everything, \"at\" once\n#\\"})
	void survivesARoundTrip(String name) {
		String[] fields = CsvLine.split(CsvLine.format(9, name, "string", new byte[] {1, 2}, "shown"));

		assertEquals(name, fields[1]);
		assertEquals("string", fields[2]);
		assertArrayEquals(new byte[] {1, 2}, HexFormat.of().parseHex(fields[3]));
		assertEquals("shown", fields[4]);
	}

	@Test
	void keepsTheDisplayStringIntact() {
		String[] fields = CsvLine.split(CsvLine.format(1, "a", "string", new byte[] {1},
				"a display, with a comma"));

		assertEquals("a display, with a comma", fields[4]);
	}

	@Test
	void quotesOnlyWhenItHasTo() {
		assertEquals("coins::abc", CsvLine.quote("coins::abc"));
		assertEquals("1200", CsvLine.quote("1200"));
		assertTrue(CsvLine.quote("a b").startsWith("\""));
		assertTrue(CsvLine.quote("a,b").startsWith("\""));
	}

	@Test
	void treatsNothingAsAnEmptyField() {
		assertEquals("", CsvLine.quote(null));
		assertEquals("", CsvLine.quote(""));
	}

	@Test
	void trimsThePaddingAroundAnUnquotedField() {
		String[] fields = CsvLine.split("1041,   coins  ,  long  , 64, 100");

		assertEquals("1041", fields[0]);
		assertEquals("coins", fields[1]);
		assertEquals("long", fields[2]);
		assertEquals("64", fields[3]);
		assertEquals("100", fields[4]);
	}

	@Test
	void keepsThePaddingInsideAQuotedField() {
		String[] fields = CsvLine.split("1, \"  padded  \", string, 01, x");

		assertEquals("  padded  ", fields[1]);
	}

	@Test
	void refusesALineThatWasCutOffMidWrite() {
		assertNull(CsvLine.split("1041, \"unterminated, long, 64, 100"));
	}

	@Test
	void readsAVersionOneLineWithNoDisplay() {
		String[] fields = CsvLine.split("1041, coins, long, 0000000000000064");

		assertEquals(4, fields.length);
		assertEquals("coins", fields[1]);
	}

	@Test
	void readsBackAnEmptyName() {
		String[] fields = CsvLine.split(CsvLine.format(1, "", "long", new byte[] {1}, null));

		assertEquals("", fields[1]);
		assertEquals("", fields[4]);
	}
}
