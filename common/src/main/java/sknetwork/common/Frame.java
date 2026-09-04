package sknetwork.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/** {@code [varint length][byte opcode][payload]}. Length covers the opcode. */
public final class Frame {

	public static final int MAX_LENGTH = 8 * 1024 * 1024;

	public final byte opcode;
	public final byte[] payload;

	public Frame(byte opcode, byte[] payload) {
		this.opcode = opcode;
		this.payload = payload;
	}

	public PacketIn reader() {
		return new PacketIn(payload);
	}

	public void write(DataOutputStream out) throws IOException {
		synchronized (out) {
			VarInt.write(out, payload.length + 1);
			out.writeByte(opcode);
			out.write(payload);
			out.flush();
		}
	}

	public static Frame read(DataInputStream in) throws IOException {
		int length = VarInt.read(in);
		if (length < 1)
			throw new IOException("frame length " + length + " is not a frame");
		if (length > MAX_LENGTH)
			throw new IOException("frame length " + length + " exceeds the " + MAX_LENGTH + " byte cap");

		byte opcode = in.readByte();
		byte[] payload = new byte[length - 1];
		in.readFully(payload);
		return new Frame(opcode, payload);
	}

	public static boolean isCleanClose(IOException e) {
		return e instanceof EOFException;
	}
}
