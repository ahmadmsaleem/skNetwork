package sknetwork.spigot;

import java.util.function.Consumer;

import sknetwork.spigot.elements.types.AtomicChange;
import sknetwork.spigot.elements.types.AtomicResult;

/** All the Skript syntax needs from the plugin, and nothing more. */
public interface NetworkAccess {

	boolean isSynced();

	String serverName();

	/** @return null when this server is set not to send display strings */
	String describe(Object value);

	/** @return false if the change could not be sent */
	boolean atomic(AtomicChange change);

	/**
	 * {@code whenAnswered} always runs, exactly once, on the main thread. A refusal,
	 * a timeout and a lost proxy all go through it, so a parked trigger can never be
	 * left where it stands.
	 */
	void atomic(AtomicChange change, Consumer<AtomicResult> whenAnswered);
}
