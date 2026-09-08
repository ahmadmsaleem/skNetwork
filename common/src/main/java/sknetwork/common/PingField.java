package sknetwork.common;

public enum PingField {

	MOTD,
	MAX_PLAYERS,
	PLAYER_COUNT;

	private static final PingField[] VALUES = values();

	public byte id() {
		return (byte) ordinal();
	}

	public static PingField byId(byte id) {
		if (id < 0 || id >= VALUES.length)
			throw new IllegalArgumentException("unknown ping field " + id);
		return VALUES[id];
	}
}
