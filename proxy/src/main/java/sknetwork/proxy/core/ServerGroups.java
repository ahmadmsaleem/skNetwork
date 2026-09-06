package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ServerGroups {

	/** Reaches every connected server and cannot be redefined. */
	public static final String GLOBAL = "global";

	private static final String GROUP_SUFFIX = "/";

	private final Map<String, List<String>> raw = new LinkedHashMap<>();
	private final List<String> problems = new ArrayList<>();

	public ServerGroups(Map<String, List<String>> configured) {
		configured.forEach((name, members) -> {
			if (GLOBAL.equalsIgnoreCase(name)) {
				problems.add("'global' is reserved and always means every server - "
						+ "remove it from the groups list");
				return;
			}
			raw.put(name, members == null ? List.of() : List.copyOf(members));
		});
		detectCycles();
	}

	/** Config mistakes worth printing at startup rather than at first push. */
	public List<String> problems() {
		return problems;
	}

	public boolean isDefined(String group) {
		return GLOBAL.equals(group) || raw.containsKey(group);
	}

	public Set<String> names() {
		return raw.keySet();
	}

	/** @return true if this folder reaches that server */
	public boolean reaches(String group, String serverName) {
		if (GLOBAL.equals(group))
			return true;
		return resolve(group, new LinkedHashSet<>()).contains(serverName);
	}

	/** Flattens a group to the server names it covers, following nested references. */
	public Set<String> resolve(String group, Set<String> seen) {
		Set<String> servers = new LinkedHashSet<>();
		if (!seen.add(group))
			return servers; // already expanded, or a cycle we have reported

		for (String member : raw.getOrDefault(group, List.of())) {
			if (member.endsWith(GROUP_SUFFIX))
				servers.addAll(resolve(member.substring(0, member.length() - 1), seen));
			else
				servers.add(member);
		}
		return servers;
	}


	private void detectCycles() {
		for (String group : raw.keySet()) {
			List<String> path = new ArrayList<>();
			if (walk(group, path, new LinkedHashSet<>()))
				problems.add("group '" + group + "' includes itself through "
						+ String.join(" -> ", path) + " - the chain has to end somewhere");
		}
	}

	private boolean walk(String group, List<String> path, Set<String> stack) {
		if (!stack.add(group)) {
			path.add(group);
			return true;
		}
		path.add(group);

		for (String member : raw.getOrDefault(group, List.of())) {
			if (!member.endsWith(GROUP_SUFFIX))
				continue;
			if (walk(member.substring(0, member.length() - 1), path, stack))
				return true;
		}

		path.remove(path.size() - 1);
		stack.remove(group);
		return false;
	}

	/** Names used by folders on disk that no group defines. */
	public List<String> undefined(Collection<String> folderNames) {
		List<String> missing = new ArrayList<>();
		for (String folder : folderNames)
			if (!isDefined(folder))
				missing.add(folder);
		return missing;
	}
}
