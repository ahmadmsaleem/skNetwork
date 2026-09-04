package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NumbersTest {

	@ParameterizedTest
	@ValueSource(strings = {"long", "integer", "short", "byte", "double", "float"})
	void knowsTheNumericTypes(String type) {
		assertTrue(Numbers.isNumeric(type));
	}

	@ParameterizedTest
	@ValueSource(strings = {"string", "player", "itemtype", "boolean", "Long", "number"})
	void treatsEverythingElseAsOpaque(String type) {
		assertFalse(Numbers.isNumeric(type));
	}

	@Test
	void tellsWholeNumbersFromFractionalOnes() {
		assertTrue(Numbers.isIntegral("long"));
		assertTrue(Numbers.isIntegral("integer"));
		assertTrue(Numbers.isIntegral("short"));
		assertTrue(Numbers.isIntegral("byte"));
		assertFalse(Numbers.isIntegral("double"));
		assertFalse(Numbers.isIntegral("float"));
	}

	@Test
	void readsTheLogLinesTheProxyWrites() {
		assertEquals(1200, Numbers.readLong("long", HexFormat.of().parseHex("00000000000004b0")));
		assertEquals(3.14, Numbers.readDouble("double", HexFormat.of().parseHex("40091eb851eb851f")));
	}

	@Test
	void survivesAWholeNumberRoundTrip() {
		for (long value : new long[] {0, 1, -1, 100, Long.MAX_VALUE, Long.MIN_VALUE, 1L << 53})
			assertEquals(value, Numbers.readLong("long", Numbers.writeLong(value)));
	}

	@Test
	void survivesAFractionalRoundTrip() {
		for (double value : new double[] {0, 1.5, -0.25, 1e300, Double.MIN_VALUE})
			assertEquals(value, Numbers.readDouble("double", Numbers.writeDouble(value)));
	}

	@Test
	void signExtendsANarrowType() {
		assertEquals(-1, Numbers.readLong("byte", new byte[] {(byte) 0xFF}));
		assertEquals(-1, Numbers.readLong("short", new byte[] {(byte) 0xFF, (byte) 0xFF}));
		assertEquals(-1, Numbers.readLong("integer",
				new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
		assertEquals(127, Numbers.readLong("byte", new byte[] {0x7F}));
		assertEquals(-128, Numbers.readLong("byte", new byte[] {(byte) 0x80}));
	}

	@Test
	void readsAFloatAsFourBytes() {
		assertEquals(1.5, Numbers.readDouble("float",
				HexFormat.of().parseHex(Integer.toHexString(Float.floatToIntBits(1.5f)))));
	}

	@Test
	void readsAWholeNumberAsADoubleToo() {
		assertEquals(100.0, Numbers.readDouble("long", Numbers.writeLong(100)));
	}

	@Test
	void refusesToReadSomethingThatIsNotANumber() {
		assertThrows(IllegalArgumentException.class, () -> Numbers.readLong("string", new byte[8]));
		assertThrows(IllegalArgumentException.class, () -> Numbers.readLong("double", new byte[8]));
		assertThrows(IllegalArgumentException.class, () -> Numbers.readDouble("string", new byte[8]));
	}

	@Test
	void printsAWholeDoubleWithoutADecimalPoint() {
		assertEquals("2", Numbers.display(2.0));
		assertEquals("0", Numbers.display(0.0));
		assertEquals("-7", Numbers.display(-7.0));
	}

	@Test
	void keepsTheFractionWhenThereIsOne() {
		assertEquals("2.5", Numbers.display(2.5));
		assertEquals("-0.25", Numbers.display(-0.25));
	}

	@Test
	void doesNotPretendAHugeDoubleIsWhole() {
		assertTrue(Numbers.display(1e20).contains("E"));
	}

	@Test
	void writesEightBigEndianBytes() {
		assertArrayEquals(HexFormat.of().parseHex("0000000000000064"), Numbers.writeLong(100));
		assertEquals(8, Numbers.writeDouble(1.5).length);
	}
}
