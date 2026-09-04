package sknetwork.common;

import java.util.concurrent.atomic.AtomicLong;

/** Lets a repeated message through at most once per interval. */
public final class Throttle {

	private final long intervalMs;
	private final AtomicLong last = new AtomicLong();

	public Throttle(long intervalMs) {
		this.intervalMs = intervalMs;
	}


	public boolean allow() {
		long now = System.currentTimeMillis();
		long previous = last.get();
		if (previous != 0 && now - previous < intervalMs)
			return false;
		return last.compareAndSet(previous, now);
	}
}
