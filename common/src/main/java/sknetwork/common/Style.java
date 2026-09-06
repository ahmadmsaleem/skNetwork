package sknetwork.common;

import java.util.Locale;

/**
 * How both halves print {@code /sknet}. Legacy section codes, because that is the
 * one format Paper, BungeeCord and Velocity can all be handed.
 */
public final class Style {

	/** The addon's own green, from the logo. */
	public static final String BRAND_HEX = "B0DD4A";

	public static final String BRAND = hex(BRAND_HEX);
	public static final String LABEL = "§7";
	public static final String VALUE = "§f";
	public static final String MUTED = "§8";
	public static final String GOOD = "§a";
	public static final String WARN = "§e";
	public static final String BAD = "§c";
	public static final String BOLD = "§l";
	public static final String RESET = "§r";

	/** Left edge every line hangs off, so the block reads as one thing. */
	private static final String EDGE = MUTED + "│ ";

	/** Width the labels are padded to. Even in chat's variable font this lines up well. */
	private static final int LABEL_WIDTH = 11;

	/** {@code §x§R§R§G§G§B§B}, which is how a hex colour travels in a legacy string. */
	public static String hex(String rgb) {
		StringBuilder out = new StringBuilder("§x");
		for (char c : rgb.toCharArray())
			out.append('§').append(c);
		return out.toString();
	}

	/**
	 * Swaps every hex colour for the nearest plain one. A player's client renders hex
	 * fine, but a console translating colours to ANSI does not, and prints a bare
	 * {@code §x} in the middle of the line instead.
	 */
	public static String downgrade(String line) {
		return HEX_PATTERN.matcher(line).replaceAll(GOOD);
	}

	private static final java.util.regex.Pattern HEX_PATTERN =
			java.util.regex.Pattern.compile("§x(?:§[0-9a-fA-F]){6}");

	/** {@code skNetwork 0.0.1 - protocol 6}, with the wordmark split like the logo. */
	public static String header(String version, int protocol) {
		return VALUE + BOLD + "sk" + BRAND + BOLD + "Network" + RESET
				+ MUTED + "  │  " + LABEL + version
				+ MUTED + "  -  " + LABEL + "protocol " + VALUE + protocol;
	}

	/** A blank line inside the block, so it still shows the edge. */
	public static String gap() {
		return MUTED + "│";
	}

	public static String row(String label, String value) {
		return EDGE + LABEL + pad(label) + VALUE + value;
	}

	/** A row whose value carries its own colour, for a state that can be bad. */
	public static String rowRaw(String label, String value) {
		return EDGE + LABEL + pad(label) + value;
	}

	/** {@code /sknet resync   pull the whole map again} */
	public static String hint(String command, String description) {
		return EDGE + BRAND + command + "  " + LABEL + description;
	}

	public static String note(String text) {
		return EDGE + MUTED + text;
	}

	/** Dimmed detail that follows a value on the same row. */
	public static String dim(String text) {
		return MUTED + text;
	}

	/** Thousands separators, because a variable count runs into six figures. */
	public static String number(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String pad(String label) {
		if (label.length() >= LABEL_WIDTH)
			return label + " ";
		return label + " ".repeat(LABEL_WIDTH - label.length());
	}

	private Style() {
	}
}
