package sknetwork.common;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Builds a frame payload. */
public final class PacketOut {

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	private final DataOutputStream out = new DataOutputStream(bytes);
	private final byte opcode;

	public PacketOut(byte opcode) {
		this.opcode = opcode;
	}

	public PacketOut varInt(int value) {
		try {
			VarInt.write(out, value);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public PacketOut int64(long value) {
		try {
			out.writeLong(value);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public PacketOut bool(boolean value) {
		try {
			out.writeBoolean(value);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public PacketOut string(String value) {
		byte[] raw = value.getBytes(StandardCharsets.UTF_8);
		varInt(raw.length);
		try {
			out.write(raw);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public PacketOut nullableString(String value) {
		bool(value != null);
		return value == null ? this : string(value);
	}

	public PacketOut nullableBytes(byte[] value) {
		bool(value != null);
		if (value == null)
			return this;
		varInt(value.length);
		try {
			out.write(value);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}


	/** Appends bytes as they are, for a payload built in pieces. */
	public PacketOut raw(byte[] payload) {
		try {
			out.write(payload);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** How many payload bytes are written so far. */
	public int size() {
		return bytes.size();
	}

	public Frame frame() {
		return new Frame(opcode, bytes.toByteArray());
	}
}
