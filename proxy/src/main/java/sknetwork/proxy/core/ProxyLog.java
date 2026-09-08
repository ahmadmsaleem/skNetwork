package sknetwork.proxy.core;

import sknetwork.common.Log;

final class ProxyLog implements Log {

	private final Log delegate;
	private volatile boolean debug;

	ProxyLog(Log delegate) {
		this.delegate = delegate;
	}

	boolean debugEnabled() {
		return debug;
	}

	void debugEnabled(boolean debug) {
		this.debug = debug;
	}

	@Override
	public void info(String message) {
		delegate.info(message);
	}

	@Override
	public void warn(String message) {
		delegate.warn(message);
	}

	@Override
	public void error(String message, Throwable error) {
		delegate.error(message, error);
	}

	@Override
	public void debug(String message) {
		if (debug)
			delegate.debug(message);
	}
}
