package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VarIntTest {

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 127, 128, 255, 16_383, 16_384, 2_097_151, 2_097_152,
			268_435_455, 268_435_456, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
	void survivesARoundTrip(int value) throws IOException {
		assertEquals(value, read(write(value)));
	}

	@Test
	void usesOneByteBelow128() throws IOException {
		assertEquals(1, write(127).length);
		assertEquals(2, write(128).length);
	}

	@Test
	void usesFiveBytesForANegative() throws IOException {
		assertEquals(5, write(-1).length);
	}

	@Test
	void setsTheContinuationBitOnEveryByteButTheLast() throws IOException {
		byte[] encoded = write(300);
		assertEquals(2, encoded.length);
		assertEquals((byte) 0xAC, encoded[0]);
		assertEquals((byte) 0x02, encoded[1]);
	}

	@Test
	void rejectsAVarIntThatNeverEnds() {
		byte[] endless = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x01};
		IOException thrown = assertThrows(IOException.class, () -> read(endless));
		assertEquals("varint is too long", thrown.getMessage());
	}

	private static byte[] write(int value) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		VarInt.write(new DataOutputStream(bytes), value);
		return bytes.toByteArray();
	}

	private static int read(byte[] encoded) throws IOException {
		return VarInt.read(new DataInputStream(new ByteArrayInputStream(encoded)));
	}
}
