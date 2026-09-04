package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FrameTest {

	@Test
	void survivesARoundTrip() throws IOException {
		byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
		Frame read = roundTrip(new Frame(Protocol.DELTA, payload));

		assertEquals(Protocol.DELTA, read.opcode);
		assertArrayEquals(payload, read.payload);
	}

	@Test
	void carriesAnEmptyPayload() throws IOException {
		Frame read = roundTrip(new Frame(Protocol.PING, new byte[0]));

		assertEquals(Protocol.PING, read.opcode);
		assertEquals(0, read.payload.length);
	}

	@Test
	void countsTheOpcodeInTheLength() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new Frame(Protocol.HELLO, new byte[4]).write(new DataOutputStream(bytes));

		byte[] written = bytes.toByteArray();
		assertEquals(5, written[0]);
		assertEquals(6, written.length);
	}

	@Test
	void readsBackToBackFrames() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		new Frame(Protocol.PING, new byte[] {1}).write(out);
		new Frame(Protocol.PONG, new byte[] {2}).write(out);

		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(Protocol.PING, Frame.read(in).opcode);
		assertEquals(Protocol.PONG, Frame.read(in).opcode);
	}

	@Test
	void rejectsALengthThatCannotHoldAnOpcode() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		VarInt.write(new DataOutputStream(bytes), 0);

		IOException thrown = assertThrows(IOException.class, () -> read(bytes.toByteArray()));
		assertTrue(thrown.getMessage().contains("is not a frame"));
	}

	@Test
	void rejectsALengthOverTheCap() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		VarInt.write(new DataOutputStream(bytes), Frame.MAX_LENGTH + 1);

		IOException thrown = assertThrows(IOException.class, () -> read(bytes.toByteArray()));
		assertTrue(thrown.getMessage().contains("exceeds"));
	}

	@Test
	void readerHandsBackThePayload() throws IOException {
		Frame frame = new PacketOut(Protocol.RESULT).int64(7).bool(true).frame();
		PacketIn packet = frame.reader();

		assertEquals(7, packet.int64());
		assertTrue(packet.bool());
	}

	@Test
	void tellsACleanCloseFromAFailure() {
		assertTrue(Frame.isCleanClose(new EOFException()));
		assertFalse(Frame.isCleanClose(new IOException("connection reset")));
	}

	private static Frame roundTrip(Frame frame) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		frame.write(new DataOutputStream(bytes));
		return read(bytes.toByteArray());
	}

	private static Frame read(byte[] encoded) throws IOException {
		return Frame.read(new DataInputStream(new ByteArrayInputStream(encoded)));
	}
}
