package sknetwork.common;

import java.io.IOException;

public record NetworkTabList(String header, String footer) {

	public void write(PacketOut out) {
		out.nullableString(header).nullableString(footer);
	}

	public static NetworkTabList read(PacketIn in) throws IOException {
		return new NetworkTabList(in.nullableString(), in.nullableString());
	}
}
