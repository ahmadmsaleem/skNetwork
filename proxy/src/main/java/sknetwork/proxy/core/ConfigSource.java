package sknetwork.proxy.core;

import java.util.List;

public interface ConfigSource {

	String string(String path, String fallback);

	int integer(String path, int fallback);

	double number(String path, double fallback);

	boolean flag(String path, boolean fallback);

	List<String> keys(String path);

	List<String> stringList(String path);
}
