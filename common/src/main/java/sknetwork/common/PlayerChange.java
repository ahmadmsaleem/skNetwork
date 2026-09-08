package sknetwork.common;

import java.io.IOException;

public record PlayerChange(Kind kind, String player, String from, String to) {

	public enum Kind {

		JOIN,
		QUIT,
		SWITCH;

		private static final Kind[] VALUES = values();

		public byte id() {
			return (byte) ordinal();
		}

		public static Kind byId(byte id) {
			if (id < 0 || id >= VALUES.length)
				throw new IllegalArgumentException("unknown player change " + id);
			return VALUES[id];
		}
	}

	public static PlayerChange joined(String player, String to) {
		return new PlayerChange(Kind.JOIN, player, null, to);
	}

	public static PlayerChange quit(String player, String from) {
		return new PlayerChange(Kind.QUIT, player, from, null);
	}

	public static PlayerChange switched(String player, String from, String to) {
		return new PlayerChange(Kind.SWITCH, player, from, to);
	}

	public void write(PacketOut out) {
		out.varInt(kind.id()).string(player).nullableString(from).nullableString(to);
	}

	public static PlayerChange read(PacketIn in) throws IOException {
		return new PlayerChange(Kind.byId((byte) in.varInt()), in.string(),
				in.nullableString(), in.nullableString());
	}
}
