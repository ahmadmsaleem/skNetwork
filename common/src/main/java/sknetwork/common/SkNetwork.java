package sknetwork.common;

/** Constants both halves have to agree on. */
public final class SkNetwork {

	public static final String NAME = "skNetwork";

	/** Configurable, because nothing guarantees '?' stays free in later Skript versions. */
	public static final String DEFAULT_PREFIX = "?";

	// bStats gives a separate id to each platform, because each ships its own Metrics
	// class. https://bstats.org/plugin/<platform>/skNetwork
	public static final int BSTATS_BUKKIT = 33851;
	public static final int BSTATS_BUNGEECORD = 33852;
	public static final int BSTATS_VELOCITY = 33853;


	private SkNetwork() {
	}
}
