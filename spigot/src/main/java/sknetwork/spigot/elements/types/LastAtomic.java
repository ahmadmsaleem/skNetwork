package sknetwork.spigot.elements.types;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.event.Event;

/**
 * What the proxy answered, kept per event so the syntax after an {@code and wait}
 * can read it. Two triggers can be waiting at once, so one shared last result
 * would let them read each other's.
 */
public final class LastAtomic {

	// weak keys: a trigger that never resumes must not pin its event here
	private static final Map<Event, AtomicResult> RESULTS =
			Collections.synchronizedMap(new WeakHashMap<>());

	public static void remember(Event event, AtomicResult result) {
		if (event != null)
			RESULTS.put(event, result);
	}

	/** @return null when nothing in this trigger has waited on the proxy yet */
	public static AtomicResult of(Event event) {
		return event == null ? null : RESULTS.get(event);
	}

	private LastAtomic() {
	}
}
