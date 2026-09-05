package sknetwork.common;

/**
 * What a {@link Protocol#PLAYER_ACTION} asks the network to do. The ordinal is the
 * wire value, so add new actions at the end, never in the middle.
 */
public enum PlayerAction {

	MESSAGE,
	ACTION_BAR,
	CONNECT;


	private static final PlayerAction[] VALUES = values();

	/** Only the proxy can move a player, so this one is never handed to a backend. */
	public boolean handledByProxy() {
		return this == CONNECT;
	}

	public byte id() {
		return (byte) ordinal();
	}

	public static PlayerAction byId(byte id) {
		if (id < 0 || id >= VALUES.length)
			throw new IllegalArgumentException("unknown player action " + id);
		return VALUES[id];
	}
}
