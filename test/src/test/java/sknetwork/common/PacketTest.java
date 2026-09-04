package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class PacketTest {

	@Test
	void readsEveryFieldBackInOrder() throws IOException {
		Frame frame = new PacketOut(Protocol.MUTATE)
				.int64(42)
				.varInt(300)
				.bool(true)
				.string("coins::eult")
				.nullableString(null)
				.nullableBytes(new byte[] {1, 2, 3})
				.frame();

		PacketIn packet = frame.reader();
		assertEquals(42, packet.int64());
		assertEquals(300, packet.varInt());
		assertTrue(packet.bool());
		assertEquals("coins::eult", packet.string());
		assertNull(packet.nullableString());
		assertArrayEquals(new byte[] {1, 2, 3}, packet.nullableBytes());
	}

	@Test
	void keepsMultiByteCharacters() throws IOException {
		String name = "café::日本語::💀";
		PacketIn packet = new PacketOut(Protocol.DELTA).string(name).frame().reader();

		assertEquals(name, packet.string());
	}

	@Test
	void keepsAnEmptyString() throws IOException {
		PacketIn packet = new PacketOut(Protocol.DELTA).string("").frame().reader();

		assertEquals("", packet.string());
	}

	@Test
	void keepsEmptyBytesApartFromNull() throws IOException {
		PacketIn packet = new PacketOut(Protocol.DELTA)
				.nullableBytes(new byte[0])
				.nullableBytes(null)
				.frame()
				.reader();

		assertArrayEquals(new byte[0], packet.nullableBytes());
		assertNull(packet.nullableBytes());
	}

	@Test
	void keepsTheFullRangeOfALong() throws IOException {
		PacketIn packet = new PacketOut(Protocol.DELTA)
				.int64(Long.MIN_VALUE)
				.int64(Long.MAX_VALUE)
				.frame()
				.reader();

		assertEquals(Long.MIN_VALUE, packet.int64());
		assertEquals(Long.MAX_VALUE, packet.int64());
	}

	@Test
	void nullableStringCostsOneByteWhenNull() throws IOException {
		assertEquals(1, new PacketOut(Protocol.DELTA).nullableString(null).frame().payload.length);
	}

	@Test
	void writesTheOpcodeItWasGiven() {
		assertEquals(Protocol.SNAPSHOT, new PacketOut(Protocol.SNAPSHOT).frame().opcode);
	}

	@Test
	void rejectsAStringLengthOverTheCap() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		VarInt.write(new DataOutputStream(bytes), Frame.MAX_LENGTH + 1);

		PacketIn packet = new PacketIn(bytes.toByteArray());
		IOException thrown = assertThrows(IOException.class, packet::string);
		assertTrue(thrown.getMessage().contains("out of range"));
	}

	@Test
	void rejectsAByteArrayLengthOverTheCap() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeBoolean(true);
		VarInt.write(out, Frame.MAX_LENGTH + 1);

		PacketIn packet = new PacketIn(bytes.toByteArray());
		IOException thrown = assertThrows(IOException.class, packet::nullableBytes);
		assertTrue(thrown.getMessage().contains("out of range"));
	}

	@Test
	void runsOutOfPayloadRatherThanInventingData() throws IOException {
		PacketIn packet = new PacketOut(Protocol.DELTA).bool(false).frame().reader();

		assertFalse(packet.bool());
		assertThrows(IOException.class, packet::int64);
	}
}
