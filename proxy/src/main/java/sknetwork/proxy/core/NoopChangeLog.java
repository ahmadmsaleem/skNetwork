package sknetwork.proxy.core;

final class NoopChangeLog implements Storage {

	@Override
	public long open(VariableStore store) {
		return 0;
	}

	@Override
	public boolean mayBeMissingKeys() {
		return false;
	}

	@Override
	public void append(long seq, String name, String type, byte[] value, String display) {
	}

	@Override
	public void noPersist(NamePatterns patterns, VariableStore store, long seq) {
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}
}
