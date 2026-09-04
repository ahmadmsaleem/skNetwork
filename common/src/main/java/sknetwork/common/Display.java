package sknetwork.common;

/**
 * The readable form of a value, carried alongside the bytes so the proxy can
 * print one without ever deserialising.
 * The proxy stores Yggdrasil bytes and has no Skript classes, so the only server
 * that can render a value is the backend that wrote it.
 */
public final class Display {

	/** Past this, deserialising a value just to print it is not worth the write thread's time. */
	public static final int MAX_VALUE_BYTES = 64 * 1024;

	/** Cap on the string itself, so a huge value still costs a fixed few bytes per write. */
	public static final int MAX_LENGTH = 120;

	/**
	 * Trims and flattens a rendered value. Newlines and control characters would
	 * break both the console output and a line of the CSV log.
	 *
	 * @return null when there is nothing worth showing
	 */
	public static String shorten(String display) {
		if (display == null || display.isEmpty())
			return null;

		StringBuilder out = new StringBuilder(Math.min(display.length(), MAX_LENGTH));
		for (int i = 0; i < display.length() && out.length() < MAX_LENGTH; i++) {
			char c = display.charAt(i);
			out.append(Character.isISOControl(c) ? ' ' : c);
		}
		if (display.length() > out.length())
			out.append('…');
		return out.toString();
	}

	private Display() {
	}
}
