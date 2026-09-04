package sknetwork.proxy.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import sknetwork.common.Log;

final class RecordingLog implements Log {

	private final List<String> lines = new CopyOnWriteArrayList<>();
	private final boolean echo;

	RecordingLog() {
		this(false);
	}

	RecordingLog(boolean echo) {
		this.echo = echo;
	}

	@Override
	public void info(String message) {
		record("INFO " + message);
	}

	@Override
	public void warn(String message) {
		record("WARN " + message);
	}

	@Override
	public void error(String message, Throwable error) {
		record("ERROR " + message + " (" + error + ")");
	}

	@Override
	public void debug(String message) {
		record("DEBUG " + message);
	}

	List<String> lines() {
		return List.copyOf(lines);
	}

	boolean sawAny(String fragment) {
		return lines.stream().anyMatch(line -> line.contains(fragment));
	}

	boolean sawWarning(String fragment) {
		return lines.stream().anyMatch(line -> line.startsWith("WARN") && line.contains(fragment));
	}

	private void record(String line) {
		lines.add(line);
		if (echo)
			System.out.println(line);
	}
}
