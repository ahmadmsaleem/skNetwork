package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import sknetwork.common.VariableEntry;
import sknetwork.common.VariableName;

/**
 * The proxy's copy of every network variable. Only the writer thread mutates it;
 * the map is concurrent so size and snapshots can be read without locking.
 */
final class VariableStore {


	private final Map<String, VariableEntry> variables = new ConcurrentHashMap<>();

	void set(String name, String type, byte[] value, String display, long seq) {
		variables.put(name, new VariableEntry(type, value, display, seq));
	}

	/** @return whether anything was there to remove, so a no-op delete costs no sequence number */
	boolean delete(String name) {
		if (!VariableName.isTree(name))
			return variables.remove(name) != null;

		String base = VariableName.treeBase(name);
		return variables.keySet().removeIf(key -> VariableName.inTree(key, base));
	}

	VariableEntry get(String name) {
		return variables.get(name);
	}

	int size() {
		return variables.size();
	}

	List<Map.Entry<String, VariableEntry>> entries() {
		return new ArrayList<>(variables.entrySet());
	}

	/**
	 * Every variable whose name matches, sorted by name so two runs of the same
	 * dump read the same way.
	 *
	 * @param glob {@code *} is a wildcard, so {@code coins::*} is a whole tree and
	 *             a pattern without one has to match the name exactly
	 */
	List<Map.Entry<String, VariableEntry>> matching(String glob) {
		Pattern pattern = compile(glob);
		return variables.entrySet().stream()
				.filter(entry -> pattern.matcher(entry.getKey()).matches())
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toList());
	}


	private static Pattern compile(String glob) {
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
