package sknetwork.common;

import java.io.IOException;

public record NetworkSound(String key, float volume, float pitch) {

	public void write(PacketOut out) {
		out.string(key).float32(volume).float32(pitch);
	}

	public static NetworkSound read(PacketIn in) throws IOException {
		return new NetworkSound(in.string(), in.float32(), in.float32());
	}
}
