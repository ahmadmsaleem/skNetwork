package sknetwork.spigot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SkriptConfigPatcher {


	enum Result {
		ALREADY_PRESENT,
		PATCHED,
		NO_CONFIG_FILE,
		NO_DATABASES_SECTION,
		FAILED
	}

	private final File configFile;
	private final String prefix;

	private String detail = "";

	SkriptConfigPatcher(File configFile, String prefix) {
		this.configFile = configFile;
		this.prefix = prefix;
	}

	String detail() {
		return detail;
	}

	Result patch() {
		if (!configFile.isFile()) {
			detail = configFile.getPath() + " does not exist yet";
			return Result.NO_CONFIG_FILE;
		}

		List<String> lines;
		try {
			lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			detail = "could not read " + configFile.getName() + ": " + e.getMessage();
			return Result.FAILED;
		}

		int sectionStart = indexOfDatabasesSection(lines);
		if (sectionStart < 0) {
			detail = "no 'databases:' section in " + configFile.getName();
			return Result.NO_DATABASES_SECTION;
		}

		int sectionEnd = endOfSection(lines, sectionStart);

		for (int i = sectionStart + 1; i < sectionEnd; i++) {
			if (lines.get(i).trim().toLowerCase(Locale.ROOT).replace(" ", "").equals("type:sknetwork")) {
				detail = "already declared on line " + (i + 1);
				return Result.ALREADY_PRESENT;
			}
		}

		String indent = detectIndent(lines, sectionStart, sectionEnd);
		if (indent == null) {
			detail = "the 'databases:' section is empty, so there is no indentation to copy";
			return Result.NO_DATABASES_SECTION;
		}

		String name = sectionNameFor(lines, sectionStart, sectionEnd, indent);
		int insertAt = insertionPoint(lines, sectionStart, sectionEnd, indent);

		try {
			lines.addAll(insertAt, block(indent, name));
			Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			detail = "could not write " + configFile.getName() + ": " + e.getMessage();
			return Result.FAILED;
		}

		detail = "added the '" + name + "' database at line " + (insertAt + 1);
		return Result.PATCHED;
	}

	private List<String> block(String indent, String name) {
		String body = indent + indent;
		return List.of(
				indent + name + ":",
				body + "# Written by skNetwork. Variables starting with '" + prefix + "' are shared across",
				body + "# the network and are never written to this server's disk.",
				body + "#",
				body + "# This has to sit above the catch-all database below: variables are saved to",
				body + "# the first database whose pattern matches, and nowhere else.",
				body + "#",
				body + "# Set 'force-skript-config: false' in plugins/skNetwork/config.yml to manage",
				body + "# this block yourself.",
				"",
				body + "type: skNetwork",
				"",
				body + "pattern: " + patternFor(prefix),
				"");
	}

	static String patternFor(String prefix) {
		if (prefix.length() == 1 && !Character.isLetterOrDigit(prefix.charAt(0)))
			return "[" + prefix + "].*";

		StringBuilder escaped = new StringBuilder();
		for (char c : prefix.toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == '_')
				escaped.append(c);
			else
				escaped.append('[').append(c).append(']');
		}
		return escaped + ".*";
	}

	private static int indexOfDatabasesSection(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && line.trim().equals("databases:"))
				return i;
		}
		return -1;
	}

	private static int endOfSection(List<String> lines, int sectionStart) {
		for (int i = sectionStart + 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.isBlank())
				continue;
			if (!Character.isWhitespace(line.charAt(0)))
				return i;
		}
		return lines.size();
	}

	private static String detectIndent(List<String> lines, int sectionStart, int sectionEnd) {
		for (int i = sectionStart + 1; i < sectionEnd; i++) {
			String line = lines.get(i);
			if (line.isBlank() || line.trim().startsWith("#"))
				continue;

			int end = 0;
			while (end < line.length() && Character.isWhitespace(line.charAt(end)))
				end++;
			if (end > 0)
				return line.substring(0, end);
		}
		return null;
	}

	private static String sectionNameFor(List<String> lines, int sectionStart, int sectionEnd, String indent) {
		for (int i = sectionStart + 1; i < sectionEnd; i++) {
			if (isHeader(lines.get(i), indent) && lines.get(i).trim().equals("network:"))
				return "skNetwork";
		}
		return "network";
	}

	private static int insertionPoint(List<String> lines, int sectionStart, int sectionEnd, String indent) {
		int fallback = sectionEnd;

		for (int i = sectionStart + 1; i < sectionEnd; i++) {
			if (!isHeader(lines.get(i), indent))
				continue;

			int bodyEnd = sectionEnd;
			for (int j = i + 1; j < sectionEnd; j++) {
				if (isHeader(lines.get(j), indent)) {
					bodyEnd = j;
					break;
				}
			}

			boolean catchAll = false;
			boolean enabled = true;

			for (int j = i + 1; j < bodyEnd; j++) {
				String value = lines.get(j).trim();

				if (value.startsWith("pattern:")) {
					String regex = value.substring("pattern:".length()).trim();
					catchAll = regex.equals(".*") || regex.equals(".+");
				} else if (value.startsWith("type:")) {
					// Skript skips disabled databases, so they cannot swallow a variable
					// and the block does not need to sit above them
					String type = value.substring("type:".length()).trim();
					enabled = !type.equalsIgnoreCase("disabled") && !type.equalsIgnoreCase("none");
				}
			}

			if (catchAll && enabled)
				return backUpOverComments(lines, i, sectionStart);
		}

		return fallback;
	}

	private static int backUpOverComments(List<String> lines, int header, int sectionStart) {
		int at = header;
		while (at - 1 > sectionStart) {
			String previous = lines.get(at - 1);
			if (previous.isBlank() || previous.trim().startsWith("#"))
				at--;
			else
				break;
		}
		return at;
	}

	private static boolean isHeader(String line, String indent) {
		return line.startsWith(indent)
				&& !line.startsWith(indent + indent)
				&& line.trim().endsWith(":")
				&& !line.trim().startsWith("#");
	}
}
