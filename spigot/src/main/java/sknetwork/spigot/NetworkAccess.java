package sknetwork.spigot;

import java.util.function.Consumer;

import sknetwork.spigot.elements.types.AtomicChange;
import sknetwork.spigot.elements.types.AtomicResult;

public interface NetworkAccess {

	boolean isSynced();

	String serverName();

	/** @return null when this server is set not to send display strings */
	String describe(Object value);

	/** @return false if the change could not be sent */
	boolean atomic(AtomicChange change);

	void atomic(AtomicChange change, Consumer<AtomicResult> whenAnswered);
}
