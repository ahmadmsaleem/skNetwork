package sknetwork.proxy.core;

/** Keeps nothing, so a proxy restart starts empty. */
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
	public void close() {
	}
}
