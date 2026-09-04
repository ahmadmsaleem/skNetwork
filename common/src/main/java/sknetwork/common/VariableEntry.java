package sknetwork.common;

/** A Skript type name and the bytes Skript already serialised. */
public final class VariableEntry {

	public final String type;
	public final byte[] value;
	/** How the writing backend rendered it, or null: only the proxy ever reads this. */
	public final String display;
	public final long seq;

	public VariableEntry(String type, byte[] value, String display, long seq) {
		this.type = type;
		this.value = value;
		this.display = display;
		this.seq = seq;
	}
}
