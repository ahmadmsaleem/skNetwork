package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import sknetwork.common.VariableEntry;
import sknetwork.common.VariableName;

final class VariableStore {


	private final Map<String, VariableEntry> variables = new ConcurrentHashMap<>();

	void set(String name, String type, byte[] value, String display, long seq) {
		variables.put(name, new VariableEntry(type, value, display, seq));
	}

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

	List<Map.Entry<String, VariableEntry>> matching(String glob) {
		Pattern pattern = NamePatterns.compile(glob);
		return variables.entrySet().stream()
				.filter(entry -> pattern.matcher(entry.getKey()).matches())
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toList());
	}



}
