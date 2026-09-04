package sknetwork.spigot;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import sknetwork.spigot.elements.types.AtomicResult;

/**
 * Atomic changes whose trigger is parked waiting for the proxy.
 * Every callback runs on the main thread, because each one restarts a Skript
 * trigger where it stopped.
 */
final class AtomicRequests {

	private record Pending(long deadline, Consumer<AtomicResult> resume) {
	}

	private final Map<Long, Pending> waiting = new ConcurrentHashMap<>();
	private final long timeoutMs;

	AtomicRequests(long timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	void expect(long requestId, Consumer<AtomicResult> resume) {
		waiting.put(requestId, new Pending(System.currentTimeMillis() + timeoutMs, resume));
	}

	/** @return false if nobody was waiting on it, which is every fire and forget change */
	boolean complete(long requestId, AtomicResult result) {
		Pending pending = waiting.remove(requestId);
		if (pending == null)
			return false;
		pending.resume().accept(result);
		return true;
	}

	/** Fails whatever will never be answered, so no trigger is left parked. */
	void sweep(boolean connected) {
		if (waiting.isEmpty())
			return;

		long now = System.currentTimeMillis();
		// resuming a trigger can start another atomic change, so iterate a copy
		for (Long requestId : new ArrayList<>(waiting.keySet())) {
			Pending pending = waiting.get(requestId);
			if (pending == null || (connected && now < pending.deadline()))
				continue;
			if (waiting.remove(requestId) == null)
				continue;

			// unanswered, not refused: the proxy may have applied it and lost the reply
			pending.resume().accept(AtomicResult.unanswered(connected
					? "the proxy did not answer within " + timeoutMs
							+ "ms, so whether it applied is unknown"
					: "lost the proxy before it answered, so whether it applied is unknown"));
		}
	}

	int pending() {
		return waiting.size();
	}
}
