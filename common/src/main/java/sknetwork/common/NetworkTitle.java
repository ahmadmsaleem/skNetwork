package sknetwork.common;

import java.io.IOException;

public record NetworkTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {

	public static final int DEFAULT_FADE_IN = 10;
	public static final int DEFAULT_STAY = 70;
	public static final int DEFAULT_FADE_OUT = 20;

	public void write(PacketOut out) {
		out.nullableString(title).nullableString(subtitle)
				.varInt(fadeIn).varInt(stay).varInt(fadeOut);
	}

	public static NetworkTitle read(PacketIn in) throws IOException {
		return new NetworkTitle(in.nullableString(), in.nullableString(),
				in.varInt(), in.varInt(), in.varInt());
	}
}
