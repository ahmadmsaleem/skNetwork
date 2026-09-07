package sknetwork.proxy.core;

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
	public long bytes() {
		return 0;
	}

	@Override
	public long dataLines() {
		return 0;
	}

	@Override
	public void close() {
	}
}
