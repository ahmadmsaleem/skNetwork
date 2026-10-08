package sknetwork.proxy.core;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LoginLimiter {

	static final int MAX_FAILURES = 5;
	static final long BLOCK_MS = 60_000;

	private record Entry(int count, long until) {
	}

	private final Map<InetAddress, Entry> entries = new ConcurrentHashMap<>();

	boolean blocked(InetAddress address, long now) {
		Entry entry = entries.get(address);
		return entry != null && entry.count >= MAX_FAILURES && entry.until > now;
	}

	boolean failed(InetAddress address, long now) {
		if (address.isLoopbackAddress())
			return false;
		entries.values().removeIf(entry -> entry.until <= now);
		Entry entry = entries.merge(address, new Entry(1, now + BLOCK_MS),
				(old, fresh) -> new Entry(old.count + 1, fresh.until));
		return entry.count == MAX_FAILURES;
	}

	void succeeded(InetAddress address) {
		entries.remove(address);
	}
}
