package sknetwork.common;

/** Opcodes and version number for the proxy/backend wire protocol. */
public final class Protocol {

	/** Bump this whenever a frame changes shape. Checked during HELLO. */
	public static final int VERSION = 9;


	public static final int DEFAULT_PORT = 25580;


	// Handshake
	public static final byte HELLO = 0x01;
	public static final byte WELCOME = 0x02;
	public static final byte REJECT = 0x03;

	// Proxy -> server
	public static final byte SNAPSHOT = 0x10;
	public static final byte DELTA = 0x11;
	/** Ends the sync phase, whether it was a full snapshot or a delta replay. */
	public static final byte SYNCED = 0x12;

	// Server -> proxy
	public static final byte MUTATE = 0x20;
	public static final byte RESULT = 0x21;

	// Both directions
	public static final byte PING = 0x40;
	public static final byte PONG = 0x41;

	// Script distribution
	/** proxy -> server: every script this server should hold, and its hash. */
	public static final byte MANIFEST = 0x50;
	/** server -> proxy: the subset it does not have. */
	public static final byte FETCH = 0x51;
	/** proxy -> server: one script's bytes. */
	public static final byte FILE = 0x52;
	/** server -> proxy: what loaded and what did not. */
	public static final byte LOAD_RESULT = 0x53;

	// Network state
	/** server -> proxy: this server's own details and who is on it. */
	public static final byte SERVER_INFO = 0x60;
	/** proxy -> server: every server on the network, as the proxy sees them. */
	public static final byte NETWORK_STATE = 0x61;

	// Acting on another server
	/** server -> proxy: do something to a player wherever they are. */
	public static final byte PLAYER_ACTION = 0x62;
	/** proxy -> server: carry out an action for a player on this server. */
	public static final byte PLAYER_DELIVERY = 0x63;
	/** server -> proxy -> server: run a command on a console. */
	public static final byte CONSOLE_COMMAND = 0x64;

	private Protocol() {
	}
}
