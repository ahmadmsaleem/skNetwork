package sknetwork.common;

/** Lets the proxy core log without knowing about Bungee or Velocity. */
public interface Log {

	void info(String message);

	void warn(String message);

	void error(String message, Throwable error);

	default void debug(String message) {
	}
}
