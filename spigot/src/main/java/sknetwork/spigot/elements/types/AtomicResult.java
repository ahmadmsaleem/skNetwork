package sknetwork.spigot.elements.types;

public record AtomicResult(boolean ok, Object value, String error, boolean answered) {

	public static AtomicResult accepted(Object value) {
		return new AtomicResult(true, value, null, true);
	}

	public static AtomicResult refused(String error) {
		return new AtomicResult(false, null, error, true);
	}

	public static AtomicResult unanswered(String reason) {
		return new AtomicResult(false, null, reason, false);
	}
}
