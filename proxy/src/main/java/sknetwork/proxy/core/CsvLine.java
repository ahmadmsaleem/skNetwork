package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The log's line format.
 *
 * 1041, coins::a1b2c3, long, 0000000000000064, 100
 * 1043, gone, , ,
 *
 * The second line is a delete tombstone: empty type, empty value.
 * The fifth field is how the writing backend rendered the value, so /sknet dump
 * has something to print. A v1 line has four fields and no display, which reads
 * back as null.
 */
final class CsvLine {


	private static final Pattern NEEDS_QUOTING = Pattern.compile(".*[,\"#\\s\\\\].*", Pattern.DOTALL);

	/** A null value writes a tombstone. */
	static String format(long seq, String name, String type, byte[] value, String display) {
		if (value == null)
			return seq + ", " + quote(name) + ", , , ";
		return seq + ", " + quote(name) + ", " + quote(type) + ", "
				+ HexFormat.of().formatHex(value) + ", " + quote(display);
	}

	static String quote(String value) {
		if (value == null)
			return "";
		if (!NEEDS_QUOTING.matcher(value).matches())
			return value;
		return '"' + escape(value) + '"';
	}


	private static String escape(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\"\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
	}
	/** @return the fields, or null if the line will not parse */
	static String[] split(String line) {
		List<String> fields = new ArrayList<>(4);
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean wasQuoted = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (quoted) {
				if (c == '\\' && i + 1 < line.length()) {
					char next = line.charAt(++i);
					field.append(switch (next) {
						case 'n' -> '\n';
						case 'r' -> '\r';
						case '\\' -> '\\';
						default -> next;
					});
				} else if (c != '"') {
					field.append(c);
				} else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else {
					quoted = false;
				}
				continue;
			}

			if (c == ',') {
				fields.add(wasQuoted ? field.toString() : field.toString().trim());
				field.setLength(0);
				wasQuoted = false;
			} else if (c == '"' && field.toString().isBlank()) {
				field.setLength(0);
				quoted = true;
				wasQuoted = true;
			} else if (wasQuoted && Character.isWhitespace(c)) {
			} else {
				field.append(c);
			}
		}

		if (quoted)
			return null; // unterminated quote, so the line was cut off mid write
		fields.add(wasQuoted ? field.toString() : field.toString().trim());
		return fields.toArray(new String[0]);
	}
	private CsvLine() {
	}
}
