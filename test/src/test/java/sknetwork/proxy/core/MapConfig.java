package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MapConfig implements ConfigSource {

	private final Map<String, Object> values = new LinkedHashMap<>();
	private final Map<String, List<String>> children = new LinkedHashMap<>();

	MapConfig set(String path, Object value) {
		values.put(path, value);
		return this;
	}

	MapConfig list(String parent, String name, List<String> members) {
		children.computeIfAbsent(parent, key -> new ArrayList<>()).add(name);
		values.put(parent + "." + name, members);
		return this;
	}

	@Override
	public String string(String path, String fallback) {
		Object value = values.get(path);
		return value == null ? fallback : String.valueOf(value);
	}

	@Override
	public int integer(String path, int fallback) {
		Object value = values.get(path);
		return value instanceof Number number ? number.intValue() : fallback;
	}

	@Override
	public double number(String path, double fallback) {
		Object value = values.get(path);
		return value instanceof Number number ? number.doubleValue() : fallback;
	}

	@Override
	public boolean flag(String path, boolean fallback) {
		Object value = values.get(path);
		return value instanceof Boolean bool ? bool : fallback;
	}

	@Override
	public List<String> keys(String path) {
		return children.getOrDefault(path, List.of());
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<String> stringList(String path) {
		Object value = values.get(path);
		return value instanceof List<?> list ? (List<String>) list : List.of();
	}
}
