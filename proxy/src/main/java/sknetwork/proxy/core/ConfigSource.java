package sknetwork.proxy.core;

import java.util.List;

/**
 * The little of a YAML file the proxy half needs, so the platform classes can
 * each use their own config library and the defaults still live in one place.
 * Paths are dotted, {@code scripts.enabled}, which is what BungeeCord's own
 * {@code Configuration} does. A key with a dot in it is therefore unreachable,
 * and always was.
 */
public interface ConfigSource {

	String string(String path, String fallback);

	int integer(String path, int fallback);

	double number(String path, double fallback);

	boolean flag(String path, boolean fallback);

	/** @return the keys directly under this path, in file order, or empty if there are none */
	List<String> keys(String path);

	List<String> stringList(String path);
}
