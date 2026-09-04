package sknetwork.proxy.core;

/** What the writer thread decided a mutation should do. */
record Outcome(Kind kind, String type, byte[] value, String display, String error) {


	enum Kind {
		SET,
		DELETE,
		UNCHANGED,
		REFUSED
	}

	static Outcome set(String type, byte[] value, String display) {
		return new Outcome(Kind.SET, type, value, display, null);
	}

	static Outcome deleted() {
		return new Outcome(Kind.DELETE, null, null, null, null);
	}

	/** Legal, but there is nothing to write. */
	static Outcome unchanged() {
		return new Outcome(Kind.UNCHANGED, null, null, null, null);
	}

	static Outcome refused(String error) {
		return new Outcome(Kind.REFUSED, null, null, null, error);
	}

	boolean applied() {
		return kind != Kind.REFUSED;
	}

	/** if anything actually has to be stored and broadcast. */
	boolean changed() {
		return kind == Kind.SET || kind == Kind.DELETE;
	}

	boolean delete() {
		return kind == Kind.DELETE;
	}
}
