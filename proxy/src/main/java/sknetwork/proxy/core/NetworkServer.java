package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import sknetwork.common.Frame;
import sknetwork.common.Log;
import sknetwork.common.MutationMode;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;
import sknetwork.common.Manifest;
import sknetwork.common.PlayerAction;
import sknetwork.common.RemoteServer;
import sknetwork.common.Style;
import sknetwork.common.VariableEntry;
import sknetwork.common.VariableName;

public final class NetworkServer {

	private static final int SNAPSHOT_CHUNK = 500;

	private static final int SNAPSHOT_CHUNK_BYTES = Frame.MAX_LENGTH / 2;

	private static final long DEFAULT_BACKLOG_BYTES = 64L * 1024 * 1024;

	private final String bindHost;
	private final int port;
	private final String token;
	private final ProxyLog log;
	private final VariableStore store = new VariableStore();
	private final ChangeLog changeLog;
	private final boolean persists;
	private final String logName;
	private final long flushIntervalMs;

	private final BlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<>();

	private final ArrayDeque<Replayable> replay = new ArrayDeque<>();
	private final int replayCapacity;

	private final Set<BackendConnection> connections = ConcurrentHashMap.newKeySet();
	private final AtomicLong sequence = new AtomicLong();

	private volatile boolean running;
	private volatile ServerSocket serverSocket;
	private ScheduledExecutorService flusher;

	private volatile ScriptLibrary scripts;

	private final NetworkState state = new NetworkState();

	private final Object stateLock = new Object();
	private volatile ProxyActions actions;
	private volatile ProxySettings booted;
	private volatile ProxySettings applied;
	private volatile ConfigReloader reloader;
	private volatile long backlogLimit = DEFAULT_BACKLOG_BYTES;
	private volatile boolean players = true;
	private volatile boolean remoteCommands;
	private volatile boolean usePlayerUuids = true;

	private record Replayable(long seq, Frame frame) {
	}

	public NetworkServer(String bindHost, int port, String token, File logFile,
			long flushIntervalMs, double compactRatio, int replayCapacity, Log log) {
		this(bindHost, port, token, logFile, flushIntervalMs, compactRatio,
				NamePatterns.none(), replayCapacity, log);
	}

	public NetworkServer(String bindHost, int port, String token, File logFile,
			long flushIntervalMs, double compactRatio, NamePatterns noPersist,
			int replayCapacity, Log log) {
		this.bindHost = bindHost;
		this.port = port;
		this.token = token;
		this.log = new ProxyLog(log);
		this.flushIntervalMs = flushIntervalMs;
		this.replayCapacity = Math.max(replayCapacity, 0);
		this.persists = logFile != null;
		this.logName = logFile == null ? "none" : logFile.getName();
		this.changeLog = logFile == null
				? new NoopChangeLog()
				: new CsvChangeLog(logFile, compactRatio, noPersist, log);
	}

	public Log log() {
		return log;
	}

	void debugEnabled(boolean debug) {
		log.debugEnabled(debug);
	}

	ProxySettings bootSettings() {
		return booted;
	}

	ProxySettings settings() {
		return applied;
	}

	void settings(ProxySettings settings) {
		this.booted = settings;
		this.applied = settings;
	}

	void applied(ProxySettings settings) {
		this.applied = settings;
	}

	public void reloader(ConfigReloader reloader) {
		this.reloader = reloader;
	}

	ConfigReload.Report reload() {
		ConfigReloader source = reloader;
		if (source == null)
			return ConfigReload.Report.failed("this proxy was started without a way to re-read its config");

		ProxySettings fresh;
		try {
			fresh = source.reread();
		} catch (IOException | RuntimeException e) {
			return ConfigReload.Report.failed(e.getMessage() == null ? e.toString() : e.getMessage());
		}
		return ConfigReload.apply(this, fresh);
	}

	public void scripts(ScriptLibrary library) {
		this.scripts = library;
	}

	public void actions(ProxyActions actions) {
		this.actions = actions;
	}

	public void features(boolean players, boolean remoteCommands) {
		this.players = players;
		this.remoteCommands = remoteCommands;
	}

	public boolean playersEnabled() {
		return players;
	}

	boolean persists() {
		return persists;
	}

	String logName() {
		return logName;
	}

	void noPersist(NamePatterns patterns) {
		changeLog.noPersist(patterns, store, sequence.get());
	}

	void refreshState() {
		synchronized (stateLock) {
			Frame frame = players ? state.frame() : NetworkState.empty();
			for (BackendConnection connection : connections)
				if (connection.isReady())
					connection.send(frame);
		}
	}

	long backlogLimit() {
		return backlogLimit;
	}

	void backlogLimit(long bytes) {
		backlogLimit = bytes;
	}

	public void usePlayerUuids(boolean usePlayerUuids) {
		this.usePlayerUuids = usePlayerUuids;
	}

	boolean usePlayerUuids() {
		return usePlayerUuids;
	}

	public boolean remoteCommandsEnabled() {
		return remoteCommands;
	}

	ScriptLibrary scripts() {
		return scripts;
	}

	public String token() {
		return token;
	}

	public long sequence() {
		return sequence.get();
	}

	public int variableCount() {
		return store.size();
	}

	public int connectionCount() {
		return connections.size();
	}

	public void start() throws IOException {
		sequence.set(changeLog.open(store));

		ServerSocket socket = new ServerSocket();
		socket.setReuseAddress(true);
		socket.bind(new InetSocketAddress(bindHost, port));
		serverSocket = socket;
		running = true;

		Thread writer = new Thread(this::writerLoop, "skNetwork-writer");
		writer.setDaemon(true);
		writer.start();

		Thread accept = new Thread(this::acceptLoop, "skNetwork-accept");
		accept.setDaemon(true);
		accept.start();

		flusher = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "skNetwork-flush");
			thread.setDaemon(true);
			return thread;
		});
		long interval = Math.max(flushIntervalMs, 1);
		flusher.scheduleWithFixedDelay(changeLog::flush, interval, interval, TimeUnit.MILLISECONDS);

		log.info("listening on " + bindHost + ":" + port);
	}

	public void stop() {
		running = false;
		if (flusher != null)
			flusher.shutdownNow();

		ServerSocket socket = serverSocket;
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
		for (BackendConnection connection : new ArrayList<>(connections))
			connection.close("proxy shutting down");

		changeLog.close();
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket client = serverSocket.accept();
				client.setTcpNoDelay(true);
				new BackendConnection(this, client).start();
			} catch (IOException e) {
				if (running)
					log.error("accept failed", e);
			}
		}
	}

	private void writerLoop() {
		while (running) {
			try {
				writeQueue.take().run();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (RuntimeException e) {
				log.error("write failed", e);
			}
		}
	}

	void register(BackendConnection connection) {
		warnIfNameTaken(connection);
		warnIfPlayerKeysDiffer(connection);
		connections.add(connection);
	}

	private void warnIfNameTaken(BackendConnection joining) {
		for (BackendConnection existing : connections) {
			if (!existing.name().equals(joining.name()))
				continue;

			log.warn(joining.name() + " connected from " + joining.address() + ", but a backend "
					+ "called " + existing.name() + " is already connected from " + existing.address()
					+ ". If that server just crashed or lost its network, this is its old socket "
					+ "still being held, and it drops on its own. Otherwise: Two servers sharing a "
					+ "name both get the same scripts, and anything keyed on the server name, such "
					+ "as {?online::" + joining.name() + "::*}, is written by both. Give each backend "
					+ "its own 'server-name' in config.yml.");
			return;
		}
	}

	private void warnIfPlayerKeysDiffer(BackendConnection joining) {
		if (joining.usePlayerUuids() == usePlayerUuids)
			return;

		log.warn(joining.name() + " has 'use player UUIDs in variable names' set to "
				+ joining.usePlayerUuids() + " in its Skript config, but this proxy expects "
				+ usePlayerUuids + ". A variable like {?coins::%player%} is a different key under "
				+ "each spelling, so the same player ends up with two of everything and neither "
				+ "server sees the other's writes. Set Skript's option the same way on every "
				+ "backend, or change it in this proxy's config to match them. Skript does not "
				+ "rename variables that already exist under the other spelling.");
	}

	void unregister(BackendConnection connection) {
		connections.remove(connection);
		synchronized (stateLock) {
			if (state.remove(connection, connection.name()))
				broadcastState();
		}
	}

	void serverInfo(BackendConnection origin, RemoteServer info) {
		synchronized (stateLock) {
			state.put(origin, info);
			broadcastState();
		}
	}

	private void broadcastState() {
		if (!players)
			return;

		Frame frame = state.frame();
		for (BackendConnection connection : connections)
			if (connection.isReady())
				connection.send(frame);
	}

	void sendStateTo(BackendConnection target) {
		if (!players)
			return;
		synchronized (stateLock) {
			target.send(state.frame());
		}
	}

	void playerAction(BackendConnection origin, PlayerAction action, List<String> targets,
			String payload) {
		if (!players) {
			log.debug("ignored " + action + " from " + origin.name() + ": player features are off");
			return;
		}

		if (action == PlayerAction.CONNECT) {
			ProxyActions platform = actions;
			if (platform == null)
				return;
			for (String player : targets)
				platform.connect(player, payload);
			return;
		}

		if (targets.isEmpty()) {
			Frame frame = delivery(action, List.of(), payload);
			for (BackendConnection connection : connections)
				if (connection.isReady())
					connection.send(frame);
			return;
		}

		state.route(targets).forEach((server, holding) -> {
			for (BackendConnection connection : connections)
				if (connection.isReady() && connection.name().equals(server))
					connection.send(delivery(action, holding, payload));
		});
	}

	private static Frame delivery(PlayerAction action, List<String> targets, String payload) {
		PacketOut out = new PacketOut(Protocol.PLAYER_DELIVERY)
				.varInt(action.id())
				.varInt(targets.size());
		targets.forEach(out::string);
		return out.string(payload).frame();
	}

	void consoleCommand(BackendConnection origin, List<String> servers, String command) {
		if (!remoteCommands) {
			log.warn(origin.name() + " tried to run '" + command + "' on another server, but "
					+ "'remote-commands' is off in the proxy config. Anyone who can write a script "
					+ "on one backend would otherwise have console on all of them.");
			return;
		}

		Frame frame = new PacketOut(Protocol.CONSOLE_COMMAND).string(command).frame();
		for (BackendConnection connection : connections) {
			if (!connection.isReady())
				continue;
			if (servers.isEmpty() || servers.contains(connection.name()))
				connection.send(frame);
		}
		log.info(origin.name() + " ran '" + command + "' on "
				+ (servers.isEmpty() ? "every server" : String.join(", ", servers)));
	}

	void submitMutation(BackendConnection origin, Mutation mutation) {
		writeQueue.add(() -> applyAndBroadcast(origin, mutation));
	}

	void submitSync(BackendConnection target, long lastSeq) {
		writeQueue.add(() -> sync(target, lastSeq));
	}

	private void applyAndBroadcast(BackendConnection origin, Mutation mutation) {
		String name = mutation.name();
		VariableEntry current = store.get(name);
		Outcome outcome;

		try {
			outcome = resolve(mutation, current);
		} catch (RuntimeException e) {
			outcome = Outcome.refused(e.getMessage() == null ? e.toString() : e.getMessage());
		}

		if (!outcome.applied()) {
			log.debug("refused " + mutation.mode() + " " + name + " from " + origin.name()
					+ ": " + outcome.error());
			origin.reply(mutation, outcome, sequence.get());
			return;
		}

		if (outcome.delete() && !store.delete(name)) {
			origin.reply(mutation, Outcome.unchanged(), sequence.get());
			return;
		}

		long seq = sequence.incrementAndGet();
		if (!outcome.delete())
			store.set(name, outcome.type(), outcome.value(), outcome.display(), seq);

		changeLog.append(seq, name, outcome.delete() ? null : outcome.type(),
				outcome.delete() ? null : outcome.value(),
				outcome.delete() ? null : outcome.display());

		Frame delta = new PacketOut(Protocol.DELTA)
				.int64(seq)
				.varInt(outcome.delete() ? MutationMode.DELETE.id() : MutationMode.SET.id())
				.string(name)
				.nullableString(outcome.delete() ? null : outcome.type())
				.nullableBytes(outcome.delete() ? null : outcome.value())
				.nullableString(current == null ? null : current.type)
				.nullableBytes(current == null ? null : current.value)
				.frame();

		remember(seq, delta);

		for (BackendConnection connection : connections) {
			if (connection.isReady())
				connection.send(seq, delta);
		}

		origin.reply(mutation, outcome, seq);
		changeLog.maybeCompact(store, seq);

		log.debug("seq " + seq + " " + mutation.mode() + " " + name + " from " + origin.name());
	}


	private Outcome resolve(Mutation mutation, VariableEntry current) {
		if (VariableName.isTree(mutation.name()) && mutation.mode() != MutationMode.DELETE)
			return Outcome.refused("{" + mutation.name() + "} is a list, so it can only be deleted");

		return switch (mutation.mode()) {
			case SET -> mutation.value() == null
					? Outcome.refused("no value attached")
					: Outcome.set(mutation.type(), mutation.value(), mutation.display());

			case DELETE -> Outcome.deleted();

			case ADD, REMOVE -> add(mutation, current);

			case REMOVE_IF_ABOVE -> removeWithFloor(mutation, current);

			case SET_IF_ABSENT -> {
				if (mutation.value() == null)
					yield Outcome.refused("no value attached");
				yield current != null
						? Outcome.refused("already set")
						: Outcome.set(mutation.type(), mutation.value(), mutation.display());
			}

			case COMPARE_AND_SET -> {
				if (mutation.value() == null)
					yield Outcome.refused("no value attached");
				byte[] expected = mutation.expectedValue();
				boolean matches = current == null
						? expected == null
						: expected != null && Arrays.equals(current.value, expected);
				yield matches
						? Outcome.set(mutation.type(), mutation.value(), mutation.display())
						: Outcome.refused("current value does not match");
			}

			default -> Outcome.refused("mode " + mutation.mode() + " is not implemented");
		};
	}


	private Outcome removeWithFloor(Mutation mutation, VariableEntry current) {
		if (mutation.expectedValue() == null || mutation.expectedType() == null
				|| !Numbers.isNumeric(mutation.expectedType()))
			return Outcome.refused("the floor is not a number");

		Outcome after = add(mutation, current);
		if (!after.applied())
			return after;

		boolean whole = Numbers.isIntegral(after.type()) && Numbers.isIntegral(mutation.expectedType());
		boolean below = whole
				? Numbers.readLong(after.type(), after.value())
						< Numbers.readLong(mutation.expectedType(), mutation.expectedValue())
				: Numbers.readDouble(after.type(), after.value())
						< Numbers.readDouble(mutation.expectedType(), mutation.expectedValue());

		if (!below)
			return after;

		String held = current == null ? "nothing"
				: (current.display == null ? current.type : current.display);
		return Outcome.refused("{" + mutation.name() + "} holds " + held
				+ ", so taking that much would put it below the floor");
	}

	private Outcome add(Mutation mutation, VariableEntry current) {
		if (mutation.value() == null)
			return Outcome.refused("no value attached");
		if (mutation.type() == null || !Numbers.isNumeric(mutation.type()))
			return Outcome.refused("can only add numbers, not '" + mutation.type() + "'");
		if (current != null && (current.value == null || !Numbers.isNumeric(current.type)))
			return Outcome.refused("{" + mutation.name() + "} holds a "
					+ current.type + ", so it cannot be added to");

		boolean whole = Numbers.isIntegral(mutation.type())
				&& (current == null || Numbers.isIntegral(current.type));
		return whole ? addWhole(mutation, current) : addFractional(mutation, current);
	}


	private Outcome addWhole(Mutation mutation, VariableEntry current) {
		long base = current == null ? 0 : Numbers.readLong(current.type, current.value);
		long delta = Numbers.readLong(mutation.type(), mutation.value());

		long result;
		try {
			result = subtracts(mutation)
					? Math.subtractExact(base, delta)
					: Math.addExact(base, delta);
		} catch (ArithmeticException overflow) {
			return Outcome.refused("the result does not fit in a whole number");
		}
		return Outcome.set("long", Numbers.writeLong(result), Long.toString(result));
	}

	private static boolean subtracts(Mutation mutation) {
		return mutation.mode() == MutationMode.REMOVE
				|| mutation.mode() == MutationMode.REMOVE_IF_ABOVE;
	}

	private Outcome addFractional(Mutation mutation, VariableEntry current) {
		double base = current == null ? 0 : Numbers.readDouble(current.type, current.value);
		double delta = Numbers.readDouble(mutation.type(), mutation.value());
		double result = subtracts(mutation) ? base - delta : base + delta;
		return Outcome.set("double", Numbers.writeDouble(result), Numbers.display(result));
	}


	private void pushTo(BackendConnection target) {
		ScriptLibrary library = scripts;
		Manifest manifest = library == null
				? new Manifest(Manifest.NO_VERSION, List.of())
				: library.manifestFor(target.name());
		if (target.manifestVersion() == manifest.version() && manifest.version() != 0)
			return;
		target.sendManifest(manifest);
	}

	public int push(boolean force) {
		ScriptLibrary library = scripts;
		if (library == null)
			return 0;

		boolean changed = library.rescan();
		if (!changed && !force)
			return 0;

		int sent = 0;
		for (BackendConnection connection : connections) {
			if (!connection.isReady())
				continue;
			connection.sendManifest(library.manifestFor(connection.name()));
			sent++;
		}
		return sent;
	}

	public boolean sharingScripts() {
		return scripts != null;
	}

	public String scriptSummary() {
		ScriptLibrary library = scripts;
		if (library == null)
			return "off";
		return library.fileCount() + " file(s) at manifest " + library.version();
	}

	public String logSummary() {
		if (!persists)
			return "off";

		long lines = changeLog.dataLines();
		long keys = store.size();
		return Style.bytes(changeLog.bytes()) + "  " + Style.number(lines) + " line(s) for "
				+ Style.number(keys) + " key(s), "
				+ String.format(Locale.ROOT, "%.1fx", lines / (double) Math.max(keys, 1));
	}

	public record LogStats(boolean persists, String name, long bytes, long dataLines,
			long liveKeys, long compactThreshold, long lastCompaction, long lastFlush) {
	}

	public LogStats logStats() {
		long keys = store.size();
		return new LogStats(persists, logName, changeLog.bytes(), changeLog.dataLines(), keys,
				changeLog.compactThreshold(keys), changeLog.lastCompaction(), changeLog.lastFlush());
	}

	public record Compaction(long linesBefore, long bytesBefore, long linesAfter, long bytesAfter) {
	}

	public Compaction compactNow() {
		if (!persists)
			return null;

		long linesBefore = changeLog.dataLines();
		long bytesBefore = changeLog.bytes();
		changeLog.compact(store, sequence.get());
		return new Compaction(linesBefore, bytesBefore, changeLog.dataLines(), changeLog.bytes());
	}

	public File backupNow() throws IOException {
		return persists ? changeLog.backup() : null;
	}

	public record Backend(String name, String address, String skriptVersion, long lastSeq,
			long behind, long queuedBytes, boolean usePlayerUuids, boolean ready) {
	}

	public List<Backend> backends() {
		long current = sequence.get();
		List<Backend> all = new ArrayList<>(connections.size());

		for (BackendConnection connection : connections)
			all.add(new Backend(connection.name(), connection.address(), connection.skriptVersion(),
					connection.lastSeq(), Math.max(current - connection.lastSeq(), 0),
					connection.queuedBytes(), connection.usePlayerUuids(), connection.isReady()));

		all.sort(Comparator.comparing(Backend::name, String::compareToIgnoreCase));
		return all;
	}

	void reportLoad(String serverName, long version, int loaded, List<String> errors,
			List<String> warnings) {
		if (errors.isEmpty()) {
			log.info("  " + serverName + ": " + loaded + " script(s) loaded from manifest " + version
					+ (warnings.isEmpty() ? "" : ", " + warnings.size() + " warning(s)"));
		} else {
			log.warn("  " + serverName + ": " + loaded + " loaded, " + errors.size()
					+ " error(s) from manifest " + version);
			for (String error : errors)
				log.warn("    -> " + error);
		}

		for (String warning : warnings)
			log.info("    ~  " + warning);
	}

	public record DumpLine(String name, String type, String value, long seq) {
	}

	public record Dump(int total, List<DumpLine> lines) {
	}

	public Dump dump(String glob, int limit) {
		List<Map.Entry<String, VariableEntry>> matched = store.matching(glob);
		List<DumpLine> lines = new ArrayList<>(Math.min(matched.size(), Math.max(limit, 0)));

		for (Map.Entry<String, VariableEntry> entry : matched) {
			if (lines.size() >= limit)
				break;
			VariableEntry variable = entry.getValue();
			String shown = variable.display != null
					? variable.display
					: "<" + (variable.value == null ? 0 : variable.value.length) + " bytes, no display>";
			lines.add(new DumpLine(entry.getKey(), variable.type, shown, variable.seq));
		}
		return new Dump(matched.size(), lines);
	}

	private void remember(long seq, Frame delta) {
		if (replayCapacity == 0)
			return;
		replay.addLast(new Replayable(seq, delta));
		while (replay.size() > replayCapacity)
			replay.removeFirst();
	}

	private void sync(BackendConnection target, long lastSeq) {
		long current = sequence.get();

		boolean plausible = lastSeq > 0 && lastSeq <= current;
		boolean covered = lastSeq == current
				|| (!replay.isEmpty() && lastSeq + 1 >= replay.peekFirst().seq());
		boolean canResume = plausible && covered;

		target.sendWelcome(current, canResume);

		if (canResume) {
			int replayed = 0;
			for (Replayable entry : replay) {
				if (entry.seq() > lastSeq) {
					target.send(entry.frame());
					replayed++;
				}
			}
			target.send(current, new PacketOut(Protocol.SYNCED).int64(current).bool(false).frame());
			target.markReady();
			sendStateTo(target);
			pushTo(target);
			log.info(target.name() + " resumed from seq " + lastSeq + ": " + replayed + " delta(s) replayed");
			return;
		}

		List<Map.Entry<String, VariableEntry>> pending = store.entries();
		int start = 0;
		while (start < pending.size()) {
			PacketOut entries = new PacketOut(Protocol.SNAPSHOT);
			int end = start;
			while (end < pending.size() && end - start < SNAPSHOT_CHUNK
					&& (end == start || entries.size() < SNAPSHOT_CHUNK_BYTES)) {
				Map.Entry<String, VariableEntry> entry = pending.get(end++);
				entries.string(entry.getKey())
						.nullableString(entry.getValue().type)
						.nullableBytes(entry.getValue().value);
			}

			target.send(new PacketOut(Protocol.SNAPSHOT)
					.varInt(end - start)
					.raw(entries.frame().payload)
					.frame());
			start = end;
		}

		target.send(current, new PacketOut(Protocol.SYNCED).int64(current).bool(true).frame());

		target.markReady();
		sendStateTo(target);
		pushTo(target);
		if (lastSeq > 0)
			log.info(target.name() + " asked to resume from seq " + lastSeq
					+ " but that is past the replay buffer - sent a full snapshot instead");
		log.info(target.name() + " synced: " + pending.size() + " variable(s) at seq " + current);
	}
}
