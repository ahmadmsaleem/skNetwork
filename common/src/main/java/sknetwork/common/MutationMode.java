package sknetwork.common;

/**
 * Mirrors Skript's {@code Changer.ChangeMode}. The ordinal is the wire value, so
 * add new modes at the end, never in the middle.
 */
public enum MutationMode {

	SET,
	DELETE,
	ADD,
	REMOVE,
	REMOVE_ALL,
	RESET,
	SET_IF_ABSENT,
	COMPARE_AND_SET,
	/** Subtract, but refuse if the result would fall below the attached floor. */
	REMOVE_IF_ABOVE;

	private static final MutationMode[] VALUES = values();

	public byte id() {
		return (byte) ordinal();
	}

	public static MutationMode byId(byte id) {
		if (id < 0 || id >= VALUES.length)
			throw new IllegalArgumentException("unknown mutation mode " + id);
		return VALUES[id];
	}
}
