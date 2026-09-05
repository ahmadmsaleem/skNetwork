package sknetwork.spigot;

import java.io.File;
import java.util.regex.Pattern;

import ch.njol.skript.config.SectionNode;
import ch.njol.skript.variables.VariablesStorage;
import org.jetbrains.annotations.Nullable;
import sknetwork.common.MutationMode;

/**
 * Skript's supported hook for putting variables somewhere other than disk.
 * Skript instantiates it reflectively through the {@code (String)} constructor,
 * so the plugin never holds the instance and the static handle stands in.
 */
public final class SkNetworkStorage extends VariablesStorage {

	private static volatile SkNetworkStorage instance;

	/**
	 * Skript builds this object before it parses the {@code pattern:} line, so the
	 * instance existing proves nothing. It only calls {@code load_i} once the
	 * pattern has compiled, and drops the storage entirely if it has not.
	 */
	private static volatile boolean accepted;

	public SkNetworkStorage(String type) {
		super(type);
		instance = this;
	}

	static SkNetworkStorage instance() {
		return instance;
	}

	public static boolean isConfigured() {
		return accepted;
	}

	public static String pattern() {
		SkNetworkStorage storage = instance;
		if (storage == null)
			return "<none>";
		Pattern namePattern = storage.getNamePattern();
		return namePattern == null ? "<unreadable>" : namePattern.pattern();
	}

	/**
	 * Whether we are Skript's catch-all. Skript stores a null pattern for a literal
	 * {@code .*}, and its accept() then hands us every variable on the server. With
	 * a prefix set we would drop each one that lacks it: not sent to the network,
	 * and not written to variables.csv either, because we told Skript we saved it.
	 */
	public static boolean isCatchAll() {
		SkNetworkStorage storage = instance;
		return accepted && storage != null && storage.getNamePattern() == null;
	}

	/**
	 * Whether Skript's pattern actually covers the prefix we were told to use.
	 * When it does not, Skript accepts our writes but persists every inbound change
	 * through the catch-all instead, which puts the whole network mirror in
	 * variables.csv.
	 */
	public static boolean coversPrefix(String prefix) {
		SkNetworkStorage storage = instance;
		if (!accepted || storage == null)
			return false;

		Pattern pattern = storage.getNamePattern();
		if (pattern == null)
			return prefix.isEmpty();
		return pattern.matcher(prefix + "probe::name").matches();
	}

	@Override
	protected boolean load_i(SectionNode sectionNode) {
		accepted = true;
		return true;
	}

	@Override
	protected void allLoaded() {
	}

	@Override
	protected boolean requiresFile() {
		return false;
	}

	@Override
	protected File getFile(String fileName) {
		throw new UnsupportedOperationException("skNetwork does not use a file");
	}

	@Override
	protected boolean connect() {
		return true;
	}

	@Override
	protected void disconnect() {
	}

	/**
	 * Called on Skript's write thread. A null type and value mean a delete, which
	 * is the proxy's contract too, so this passes straight through once the echo
	 * check and the prefix are dealt with.
	 */
	@Override
	protected boolean save(String name, @Nullable String type, byte @Nullable [] value) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return true;

		// our own delta coming back around. dropping it here is what stops the
		// set, save, broadcast, set loop
		if (SkriptBridge.isEcho(name, value))
			return true;

		String wireName = plugin.stripPrefix(name);
		if (wireName == null) {
			plugin.warnPrefixMismatch(name);
			return true;
		}

		MutationMode mode = value == null ? MutationMode.DELETE : MutationMode.SET;
		// the proxy never deserialises, so the one chance to render this value for
		// /sknet dump is here, on the server that owns the write
		// Skript has already written this to its own map, so a refusal leaves this
		// server holding a value the network never took
		if (!plugin.sendMutation(mode, wireName, type, value, plugin.describe(type, value)))
			plugin.warnDroppedWrite(name, true);
		return true;
	}
}
