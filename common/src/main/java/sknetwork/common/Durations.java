package sknetwork.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the short durations the config files use: {@code 100ms}, {@code 5s}, {@code 2h}. */
public final class Durations {

	private static final Pattern PATTERN = Pattern.compile("([0-9]+)\\s*(ms|s|m|h)?");

	public static long millis(String value, long fallback) {
		if (value == null)
			return fallback;

		Matcher matcher = PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT));
		if (!matcher.matches())
			return fallback;

		long amount = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);
		if (unit == null)
			return amount;

		return switch (unit) {
			case "ms" -> amount;
			case "s" -> amount * 1_000;
			case "m" -> amount * 60_000;
			case "h" -> amount * 3_600_000;
			default -> fallback;
		};
	}

	private Durations() {
	}
}
