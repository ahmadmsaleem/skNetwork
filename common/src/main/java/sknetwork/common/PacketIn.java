package sknetwork.common;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads a payload written by {@link PacketOut}. */
public final class PacketIn {

	private final DataInputStream in;

	public PacketIn(byte[] payload) {
		this.in = new DataInputStream(new ByteArrayInputStream(payload));
	}

	public int varInt() throws IOException {
		return VarInt.read(in);
	}

	public long int64() throws IOException {
		return in.readLong();
	}

	public boolean bool() throws IOException {
		return in.readBoolean();
	}

	public String string() throws IOException {
		int length = varInt();
		if (length < 0 || length > Frame.MAX_LENGTH)
			throw new IOException("string length " + length + " is out of range");
		byte[] raw = new byte[length];
		in.readFully(raw);
		return new String(raw, StandardCharsets.UTF_8);
	}

	public String nullableString() throws IOException {
		return bool() ? string() : null;
	}

	public byte[] nullableBytes() throws IOException {
		if (!bool())
			return null;
		int length = varInt();
		if (length < 0 || length > Frame.MAX_LENGTH)
			throw new IOException("byte array length " + length + " is out of range");
		byte[] raw = new byte[length];
		in.readFully(raw);
		return raw;
	}
}
