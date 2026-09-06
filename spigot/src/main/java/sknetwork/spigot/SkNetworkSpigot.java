package sknetwork.spigot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.util.Version;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import ch.njol.skript.variables.Variables;
import sknetwork.common.PlayerAction;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;
import sknetwork.common.SkNetwork;
import sknetwork.common.MutationMode;
import sknetwork.common.Throttle;
import sknetwork.spigot.elements.events.NetworkDisconnectEvent;
import sknetwork.spigot.elements.events.NetworkSyncEvent;
import sknetwork.spigot.elements.types.AtomicChange;
import sknetwork.spigot.elements.types.AtomicResult;
import sknetwork.spigot.modules.NetworkModule;
import sknetwork.spigot.update.UpdateChecker;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The backend half. Skript runs here, so this is where prefixed variables get
 * routed away from variables.csv and up to the proxy.
 */
public final class SkNetworkSpigot extends JavaPlugin implements NetworkAccess {

	private static SkNetworkSpigot instance;

	private static final Version OLDEST_SKRIPT = new Version(2, 16, 0);

	private SkNetworkConfig config;
	private ProxyClient client;
	private DeltaApplier applier;
	private ScriptSync scripts;
	private AtomicRequests requests;
	private final NetworkCache network = new NetworkCache();

	private final Throttle dropWarnings = new Throttle(10_000);
	private final AtomicLong droppedWrites = new AtomicLong();
	private volatile boolean warnedPrefixMismatch;

	public static SkNetworkSpigot get() {
		return instance;
	}

	@Override
	public void onLoad() {
		instance = this;
		saveDefaultConfig();
		config = new SkNetworkConfig(getConfig(), getServer().getPort());

		// has to be before Skript parses config.sk, which it does in its own onEnable
		if (config.forceSkriptConfig())
			configureSkript(false);

		if (config.prefix().indexOf('#') >= 0) {
			// registering anyway would make us Skript's catch-all, and save() would then
			// drop every variable without our prefix instead of letting variables.csv have it
			getLogger().severe("not registering the network storage: '" + config.prefix()
					+ "' cannot be a prefix. Variables stay on this server's own disk until "
					+ "'prefix' in plugins/skNetwork/config.yml is changed.");
			return;
		}

		if (!Variables.registerStorage(SkNetworkStorage.class, "skNetwork", "sknetwork"))
			getLogger().severe("Skript refused the 'skNetwork' storage registration - "
					+ "network variables will not work.");
	}

	@Override
	public void onEnable() {
		if (Skript.getVersion().isSmallerThan(OLDEST_SKRIPT)) {
			getLogger().severe(SkNetwork.NAME + " " + getPluginMeta().getVersion() + " needs Skript "
					+ OLDEST_SKRIPT + " or newer, and this server runs Skript " + Skript.getVersion()
					+ ". Network variables are off until Skript is updated: every write to a '"
					+ config.prefix() + "' variable is refused, and none of the network syntax loads. "
					+ "Nothing in variables.csv is touched.");
			return;
		}

		getLogger().info(SkNetwork.NAME + " " + getPluginMeta().getVersion()
				+ " (protocol " + Protocol.VERSION + ") running as a BACKEND half, known as "
				+ config.serverName());
		if (!SkriptBridge.hasFastPath())
			getLogger().info("Variables.variableLoaded is unavailable on this Skript build, "
					+ "falling back to echo suppression for sets as well as deletes");

		requests = new AtomicRequests(config.atomicTimeoutMs());
		client = new ProxyClient(this, config.host(), config.port(), config.token(), config.serverName());
		scripts = new ScriptSync(this, new File(skriptDataFolder(), "scripts"));
		applier = new DeltaApplier(this, client.inboundQueue());
		getServer().getPluginManager().registerEvents(new PlayerWatch(this), this);
		applier.runTaskTimer(this, 1L, 1L);
		client.start();

		startMetrics();
		if (config.checkForUpdates())
			UpdateChecker.enable(this);
		getCommand("sknet").setExecutor(new SknetCommand(this));
		Skript.instance().registerAddon(SkNetworkSpigot.class, "skNetwork")
				.loadModules(new NetworkModule());
		getServer().getScheduler().runTaskTimer(this, () -> client.ping(), 100L, 100L);

		// Skript loads its variables in its own onEnable, which has not run yet
		getServer().getScheduler().runTask(this, this::reportStorageState);
		getServer().getScheduler().runTask(this, this::reportPushedScripts);
	}

	/**
	 * Charts are chosen to answer the questions the design notes leave open: whether
	 * '?' is holding up as a default, and how many people pay for display strings.
	 */
	private void startMetrics() {
		Metrics metrics = new Metrics(this, SkNetwork.BSTATS_BUKKIT);
		metrics.addCustomChart(new SimplePie("prefix", () -> config.prefix()));
		metrics.addCustomChart(new SimplePie("skript_version", this::skriptVersion));
		metrics.addCustomChart(new SimplePie("display_values",
				() -> config.displayValues() ? "on" : "off"));
		metrics.addCustomChart(new SimplePie("pushed_scripts",
				() -> scripts != null && scripts.fileCount() > 0 ? "yes" : "no"));
	}

	@Override
	public void onDisable() {
		if (applier != null)
			applier.cancel();
		if (client != null)
			client.stop();
		// the applier is gone, so nothing else would sweep these
		if (requests != null)
			requests.sweep(false);
	}

	/**
	 * Skript's 'use player UUIDs in variable names'. It decides whether
	 * {@code {?coins::%player%}} is keyed by UUID or by name, so two backends that
	 * disagree write the same player to two different keys and neither sees the other.
	 */
	public boolean usePlayerUuids() {
		return SkriptConfig.usePlayerUUIDsInVariableNames.value();
	}

	@Override
	public String serverName() {
		return config.serverName();
	}

	@Override
	public boolean isSynced() {
		return client != null && client.state() == SyncState.READY;
	}

	@Override
	public boolean atomic(AtomicChange change) {
		return send(change, false) != 0;
	}

	@Override
	public void atomic(AtomicChange change, Consumer<AtomicResult> whenAnswered) {
		long requestId = send(change, true);
		if (requestId == 0) {
			// nothing left the server, so nothing will answer. resume now instead of
			// parking the trigger for a reply that cannot come.
			whenAnswered.accept(AtomicResult.refused("not synced with the proxy, so the change "
					+ "was not sent"));
			return;
		}
		requests.expect(requestId, whenAnswered);
	}

	/** @return the request id, or 0 if the change could not be sent */
	private long send(AtomicChange change, boolean returnable) {
		String wire = stripPrefix(change.localName());
		if (wire == null) {
			warnPrefixMismatch(change.localName());
			return 0;
		}

		long requestId = client == null ? 0 : client.sendMutation(change.mode(), wire,
				change.type(), change.value(), change.expectedType(), change.expectedValue(),
				change.display(), returnable);
		if (requestId == 0)
			// an atomic never touches Skript's own map, so there is nothing local to repair
			warnDroppedWrite(change.localName(), false);
		return requestId;
	}

	/** @param targets empty for every player on the network */
	public boolean playerAction(PlayerAction action, List<String> targets, String payload) {
		return client != null && client.sendPlayerAction(action, targets, payload);
	}

	/** @param servers empty for every server on the network */
	public boolean consoleCommand(List<String> servers, String command) {
		return client != null && client.sendConsoleCommand(servers, command);
	}

	public NetworkCache network() {
		return network;
	}

	/** Tells the proxy what this server is and who is on it right now. */
	void reportServerInfo() {
		if (client == null)
			return;

		List<String> online = new ArrayList<>();
		for (Player player : getServer().getOnlinePlayers())
			online.add(player.getName());

		List<String> whitelisted = new ArrayList<>();
		for (OfflinePlayer player : getServer().getWhitelistedPlayers())
			if (player.getName() != null)
				whitelisted.add(player.getName());

		client.sendServerInfo(new RemoteServer(serverName(),
				getServer().getMotd(), getServer().getVersion(),
				getServer().getMaxPlayers(), online, whitelisted));
	}

	ProxyClient client() {
		return client;
	}

	/** Skript's write thread can reach us before onEnable built the client. */
	boolean sendMutation(MutationMode mode, String wireName, String type, byte[] value, String display) {
		return client != null && client.sendMutation(mode, wireName, type, value, display);
	}

	/**
	 * How a value reads, for {@code /sknetproxy dump}. Costs one deserialise on Skript's
	 * write thread, which is why it is a config option.
	 *
	 * @return null when there is nothing to show
	 */
	String describe(String type, byte[] value) {
		return config.displayValues() ? SkriptBridge.describe(type, value) : null;
	}

	@Override
	public String describe(Object value) {
		return config.displayValues() ? SkriptBridge.describe(value) : null;
	}

	AtomicRequests requests() {
		return requests;
	}

	DeltaApplier applier() {
		return applier;
	}

	ScriptSync scripts() {
		return scripts;
	}

	/** Sent in HELLO so the proxy only pushes when this server is actually behind. */
	long manifestVersion() {
		return scripts == null ? 0 : scripts.appliedVersion();
	}

	long droppedWrites() {
		return droppedWrites.get();
	}

	String prefix() {
		return config.prefix();
	}

	String skriptVersion() {
		try {
			return Skript.getVersion().toString();
		} catch (RuntimeException e) {
			return "unknown";
		}
	}

	String stripPrefix(String name) {
		return config.stripPrefix(name);
	}

	void onSynced() {
		reportServerInfo();
		fire(new NetworkSyncEvent());
	}

	void onDisconnected() {
		network.clear();
		fire(new NetworkDisconnectEvent());
	}

	private void fire(org.bukkit.event.Event event) {
		if (isEnabled())
			getServer().getScheduler().runTask(this, () -> getServer().getPluginManager().callEvent(event));
	}

	void warnPrefixMismatch(String name) {
		if (warnedPrefixMismatch)
			return;
		warnedPrefixMismatch = true;
		getLogger().warning("Skript routed {" + name + "} to us but it does not start with '" + config.prefix()
				+ "'. The 'prefix' in config.yml and the 'pattern' of the skNetwork database in "
				+ "plugins/Skript/config.sk have to agree.");
	}

	/**
	 * @param appliedLocally whether Skript already put this value in its own map.
	 *                       Only then is the copy on this server wrong, and only
	 *                       then is a full snapshot needed to put it right.
	 */
	void warnDroppedWrite(String name, boolean appliedLocally) {
		droppedWrites.incrementAndGet();
		if (appliedLocally && applier != null)
			applier.markUnsynced(name);
		if (!dropWarnings.allow())
			return;

		getLogger().warning("refused a write to {" + name + "}: not synced with the proxy ("
				+ (client == null ? SyncState.DISCONNECTED : client.state()) + "). "
				+ droppedWrites.get() + " write(s) dropped so far. "
				+ "Guard writes with 'network is synced' - before the first sync every "
				+ "'is not set' check lies.");
	}


	private void reportPushedScripts() {
		int held = scripts == null ? 0 : scripts.fileCount();
		if (held == 0)
			return;
		getLogger().info("holding " + held + " script(s) pushed by the proxy, in "
				+ "plugins/Skript/scripts/skNetwork/. Turning script distribution off on the "
				+ "proxy clears them from here on the next connect.");
	}

	private void reportStorageState() {
		// an empty prefix plus a catch-all pattern is a real choice: share everything.
		// a prefix with a catch-all pattern is not, because we would drop the rest.
		if (SkNetworkStorage.isCatchAll() && !config.prefix().isEmpty()) {
			refuseStorage("has a catch-all 'pattern', so Skript hands us every variable on this "
					+ "server. Every variable without '" + config.prefix() + "' is being dropped "
					+ "right now: not sent to the network, and not written to variables.csv "
					+ "either.");
			return;
		}
		if (SkNetworkStorage.isConfigured()) {
			if (!SkNetworkStorage.coversPrefix(config.prefix())) {
				refuseStorage("has a 'pattern' that does not cover '" + config.prefix()
						+ "'. Skript would take our writes and then persist every change the "
						+ "network sends back through the catch-all database, so the whole "
						+ "network would end up in this server's variables.csv.");
				return;
			}
			getLogger().info("Skript is routing '" + config.prefix() + "' variables to us (pattern "
					+ SkNetworkStorage.pattern() + ")");
			return;
		}

		// a brand new server lands here, because Skript had not unpacked config.sk
		// when our onLoad ran. writing it now takes effect on the next start
		if (config.forceSkriptConfig() && configureSkript(true)) {
			getLogger().warning("network variables are going to variables.csv until this server is "
					+ "restarted once.");
			return;
		}

		// Staying connected without a storage is worse than being offline: every
		// inbound change would be written to variables.csv by the catch-all
		// database instead of ours.
		stopNetworkVariables();

		getLogger().warning("no 'skNetwork' database is configured in plugins/Skript/config.sk,");
		getLogger().warning("so network variables are disabled on this server. Add, ABOVE the");
		getLogger().warning("catch-all 'default' block:");
		getLogger().warning("    network:");
		getLogger().warning("        type: skNetwork");
		getLogger().warning("        pattern: " + SkriptConfigPatcher.patternFor(config.prefix()));
	}

	private void stopNetworkVariables() {
		if (client != null)
			client.stop();
		if (applier != null)
			applier.cancel();
	}

	/**
	 * Skript could not read our {@code pattern:} line the way we meant it, so it
	 * either hands us every variable on the server or persists our inbound changes
	 * itself. Either way this server would lose or leak data, and stopping is the
	 * only safe answer.
	 *
	 * @param problem what is wrong with the database block, as one sentence
	 */
	private void refuseStorage(String problem) {
		stopNetworkVariables();

		getLogger().severe("The skNetwork database in plugins/Skript/config.sk " + problem);
		getLogger().severe("Network variables are disabled here until that line is fixed.");
		if (config.prefix().indexOf('#') >= 0)
			getLogger().severe("A '#' starts a comment in config.sk, so it cannot be a prefix at all. "
					+ "Change 'prefix' in plugins/skNetwork/config.yml.");
		else
			getLogger().severe("    pattern: " + SkriptConfigPatcher.patternFor(config.prefix()));
	}

	/**
	 * @param retry whether this is the post startup attempt for a first boot
	 * @return whether the config was changed
	 */
	private boolean configureSkript(boolean retry) {
		// '#' opens a comment in config.sk, so the pattern line would be cut in half and
		// Skript would read no pattern at all, which makes us the catch-all
		if (config.prefix().indexOf('#') >= 0) {
			getLogger().severe("'" + config.prefix() + "' cannot be a prefix: '#' starts a comment "
					+ "in plugins/Skript/config.sk, so the pattern line would never be read. "
					+ "Pick another character in plugins/skNetwork/config.yml.");
			return false;
		}

		File configSk = new File(skriptDataFolder(), "config.sk");
		SkriptConfigPatcher patcher = new SkriptConfigPatcher(configSk, config.prefix());
		SkriptConfigPatcher.Result result = patcher.patch();

		switch (result) {
			case PATCHED -> getLogger().info("configured Skript for you: " + patcher.detail());
			case ALREADY_PRESENT -> {
			}
			case NO_CONFIG_FILE -> {
				if (retry)
					getLogger().warning("could not configure Skript: " + patcher.detail());
			}
			default -> getLogger().warning("could not configure Skript automatically ("
					+ patcher.detail() + "), so this has to be done by hand.");
		}
		return result == SkriptConfigPatcher.Result.PATCHED;
	}

	private File skriptDataFolder() {
		try {
			return Skript.getInstance().getDataFolder();
		} catch (RuntimeException e) {
			return new File(getDataFolder().getParentFile(), "Skript");
		}
	}

}
