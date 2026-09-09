package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sknetwork.common.PacketOut;
import sknetwork.common.VariableEntry;

/**
 * The chunker decides whether a value fits by working its size out rather than by
 * writing it, so that answer has to be exactly what PacketOut would have written.
 */
class SnapshotSizeTest {

	@Test
	void matchesWhatIsActuallyWrittenForAnOrdinaryEntry() {
		assertMeasured("coins::eult", "long", new byte[8]);
	}

	@Test
	void matchesAcrossEveryVarIntWidth() {
		for (int length : new int[] { 0, 1, 127, 128, 16_383, 16_384, 2_097_151, 2_097_152 })
			assertMeasured("blob", "string", new byte[length]);
	}

	@Test
	void countsMultiByteNamesByTheirBytesNotTheirCharacters() {
		assertMeasured("café::ünïcode::☃", "string", new byte[10]);
	}

	@Test
	void handlesAnEntryMissingItsTypeOrValue() {
		assertMeasured("gone", null, null);
		assertMeasured("typeless", null, new byte[4]);
		assertMeasured("valueless", "string", null);
	}

	@Test
	void countsALongNameByItsOwnVarIntWidth() {
		assertMeasured("x".repeat(200), "string", new byte[1]);
	}

	private static void assertMeasured(String name, String type, byte[] value) {
		Map.Entry<String, VariableEntry> entry =
				Map.entry(name, new VariableEntry(type, value, null, 1));

		PacketOut written = PacketOut.body();
		written.string(name).nullableString(type).nullableBytes(value);

		assertEquals(written.payload().length, NetworkServer.entrySize(entry),
				"measured size differs from what PacketOut writes for " + name
						+ " (" + (value == null ? "no value" : value.length + " bytes") + ")");
	}
}
