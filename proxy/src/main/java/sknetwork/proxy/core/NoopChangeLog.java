package sknetwork.proxy.core;

import java.io.File;

final class NoopChangeLog implements ChangeLog {


	@Override
	public long open(VariableStore store) {
		return 0;
	}

	@Override
	public void append(long seq, String name, String type, byte[] value, String display) {
	}

	@Override
	public void flush() {
	}

	@Override
	public void maybeCompact(VariableStore store, long seq) {
	}

	@Override
	public void compact(VariableStore store, long seq) {
	}

	@Override
	public long bytes() {
		return 0;
	}

	@Override
	public long dataLines() {
		return 0;
	}

	@Override
	public long lastCompaction() {
		return 0;
	}

	@Override
	public long lastFlush() {
		return 0;
	}

	@Override
	public long compactThreshold(long liveKeys) {
		return 0;
	}

	@Override
	public File backup() {
		return null;
	}

	@Override
	public void close() {
	}
}
