package sknetwork.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Seven bits per byte, as Minecraft does it. */
public final class VarInt {

	public static void write(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	public static int read(DataInputStream in) throws IOException {
		int result = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			byte read = in.readByte();
			result |= (read & 0x7F) << shift;
			if ((read & 0x80) == 0)
				return result;
		}
		throw new IOException("varint is too long");
	}

	private VarInt() {
	}
}
