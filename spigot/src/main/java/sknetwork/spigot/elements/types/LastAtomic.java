package sknetwork.spigot.elements.types;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.event.Event;

public final class LastAtomic {

	private static final Map<Event, AtomicResult> RESULTS =
			Collections.synchronizedMap(new WeakHashMap<>());

	public static void remember(Event event, AtomicResult result) {
		if (event != null)
			RESULTS.put(event, result);
	}

	public static AtomicResult of(Event event) {
		return event == null ? null : RESULTS.get(event);
	}

	private LastAtomic() {
	}
}
