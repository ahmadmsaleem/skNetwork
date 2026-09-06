package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A set of variable-name globs, matched as a whole. {@code *} is the only
 * wildcard, so {@code session::*} is a whole tree and a pattern without one has
 * to match the name exactly - the same spelling {@code /sknetproxy dump} takes.
 */
final class NamePatterns {

	private static final NamePatterns NONE = new NamePatterns(List.of());

	private final List<Pattern> patterns;

	private NamePatterns(List<Pattern> patterns) {
		this.patterns = patterns;
	}

	static NamePatterns none() {
		return NONE;
	}

	/** Blank entries are dropped, so a stray '- ' in the config does not match everything. */
	static NamePatterns of(Collection<String> globs) {
		if (globs == null || globs.isEmpty())
			return NONE;

		List<Pattern> compiled = new ArrayList<>(globs.size());
		for (String glob : globs) {
			if (glob != null && !glob.isBlank())
				compiled.add(compile(glob.trim()));
		}
		return compiled.isEmpty() ? NONE : new NamePatterns(compiled);
	}

	boolean isEmpty() {
		return patterns.isEmpty();
	}

	int size() {
		return patterns.size();
	}

	boolean matches(String name) {
		if (name == null)
			return false;
		for (Pattern pattern : patterns) {
			if (pattern.matcher(name).matches())
				return true;
		}
		return false;
	}

	/**
	 * A glob as a whole-string regex. Everything but {@code *} is quoted, so a name
	 * holding regex punctuation is matched literally.
	 */
	static Pattern compile(String glob) {
		StringBuilder regex = new StringBuilder();
		int from = 0;
		for (int star = glob.indexOf('*'); star >= 0; star = glob.indexOf('*', from)) {
			if (star > from)
				regex.append(Pattern.quote(glob.substring(from, star)));
			regex.append(".*");
			from = star + 1;
		}
		if (from < glob.length())
			regex.append(Pattern.quote(glob.substring(from)));

		try {
			return Pattern.compile(regex.toString(), Pattern.DOTALL);
		} catch (PatternSyntaxException e) {
			return Pattern.compile(Pattern.quote(glob));
		}
	}
}
