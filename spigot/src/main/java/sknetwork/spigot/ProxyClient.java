package sknetwork.spigot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import sknetwork.common.Frame;
import sknetwork.common.MutationMode;
import sknetwork.common.PacketIn;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerAction;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

final class ProxyClient {

	private static final long RETRY_MIN_MS = 1_000;
	private static final long RETRY_MAX_MS = 30_000;

	/**
	 * Pings go out every five seconds, so this is half a minute of silence. A proxy
	 * that is gone without closing the socket, which is what a cable pull or a
	 * hard reboot looks like, otherwise stays "connected" for the fifteen minutes
	 * TCP takes to give up, and every write in that time is accepted and lost.
	 */
	private static final int PINGS_BEFORE_GIVING_UP = 6;

	private final SkNetworkSpigot plugin;
	private final String host;
	private final int port;
	private final String token;
	private final String serverName;

	private static final Frame POISON = new Frame((byte) 0, new byte[0]);

	/**
	 * Replaced on every connection, and that matters. Share it and a dead session's
	 * write thread picks up a frame, writes it to a closed socket, and tears down
	 * the live session instead.
	 */
	private volatile BlockingQueue<Frame> outbound;
	private final Queue<Frame> inbound = new ConcurrentLinkedQueue<>();
	private final AtomicLong requestIds = new AtomicLong();

	private volatile SyncState state = SyncState.DISCONNECTED;
	private volatile boolean running;
	private volatile Socket socket;
	private volatile String lastError;
	private volatile long latencyMs = -1;
	private volatile long syncedAt;

	/** Main thread only: ping() runs on the scheduler and PONG lands via the applier. */
	private int unansweredPings;

	/** Sent in HELLO, so a brief disconnect costs a few deltas instead of the whole map. */
	private volatile long lastSeq;

	/**
	 * Grows while the proxy is unreachable. Reset it on a successful handshake, not
	 * at the end of a session: a session only ever ends by throwing, so resetting
	 * there is dead code and a server that saw a few blips waits the full backoff
	 * against a healthy proxy.
	 */
	private long retryDelay = RETRY_MIN_MS;

	ProxyClient(SkNetworkSpigot plugin, String host, int port, String token, String serverName) {
		this.plugin = plugin;
		this.host = host;
		this.port = port;
		this.token = token;
		this.serverName = serverName;
	}

	SyncState state() {
		return state;
	}

	String lastError() {
		return lastError;
	}

	long syncedAt() {
		return syncedAt;
	}

	String describeTarget() {
		return host + ":" + port;
	}

	Queue<Frame> inboundQueue() {
		return inbound;
	}

	/** @return false when the session that finished syncing has already gone away */
	boolean markReady() {
		if (outbound == null)
			return false;
		state = SyncState.READY;
		syncedAt = System.currentTimeMillis();
		unansweredPings = 0;
		return true;
	}

	/** @return round trip to the proxy in ms, or -1 before the first reply */
	long latencyMs() {
		return latencyMs;
	}

	void recordPong(long sentAt) {
		latencyMs = Math.max(0, System.currentTimeMillis() - sentAt);
		unansweredPings = 0;
	}

	void ping() {
		BlockingQueue<Frame> queue = outbound;
		if (state != SyncState.READY || queue == null) {
			unansweredPings = 0;
			return;
		}

		if (unansweredPings >= PINGS_BEFORE_GIVING_UP) {
			unansweredPings = 0;
			lastError = "no answer to " + PINGS_BEFORE_GIVING_UP + " pings";
			plugin.getLogger().warning("the proxy has not answered the last " + PINGS_BEFORE_GIVING_UP
					+ " pings, dropping the connection. Writes since the last answer may never have "
					+ "arrived, so the next sync pulls everything rather than resuming.");
			// resuming would keep whatever was written into the void as this server's truth
			DeltaApplier applier = plugin.applier();
			if (applier != null)
				applier.requestFullSnapshot();
			dropConnection();
			return;
		}

		unansweredPings++;
		queue.add(new PacketOut(Protocol.PING).int64(System.currentTimeMillis()).frame());
	}

	long lastSeq() {
		return lastSeq;
	}

	void setLastSeq(long seq) {
		lastSeq = seq;
	}

	void start() {
		running = true;
		Thread thread = new Thread(this::connectLoop, "skNetwork-client");
		thread.setDaemon(true);
		thread.start();
	}

	void stop() {
		running = false;
		closeSocket();
	}

	/**
	 * Drops the connection but keeps {@link #lastSeq}, so the reconnect resumes
	 * instead of pulling everything. {@code /sknet reconnect} uses it.
	 */
	void dropConnection() {
		closeSocket();
	}


	/** Queues a frame if there is a live session. Used by the script sync. */
	boolean send(Frame frame) {
		BlockingQueue<Frame> queue = outbound;
		if (queue == null)
			return false;
		queue.add(frame);
		return true;
	}

	/** Tells the proxy what this server is and who is on it. */
	boolean sendServerInfo(RemoteServer info) {
		PacketOut out = new PacketOut(Protocol.SERVER_INFO);
		info.write(out);
		return send(out.frame());
	}

	/** @param targets empty means every player on the network */
	boolean sendPlayerAction(PlayerAction action, List<String> targets, String payload) {
		PacketOut out = new PacketOut(Protocol.PLAYER_ACTION)
				.varInt(action.id())
				.varInt(targets.size());
		targets.forEach(out::string);
		return send(out.string(payload).frame());
	}

	/** @param servers empty means every server on the network */
	boolean sendConsoleCommand(List<String> servers, String command) {
		PacketOut out = new PacketOut(Protocol.CONSOLE_COMMAND).varInt(servers.size());
		servers.forEach(out::string);
		return send(out.string(command).frame());
	}

	boolean sendMutation(MutationMode mode, String name, String type, byte[] value, String display) {
		return sendMutation(mode, name, type, value, null, null, display, false) != 0;
	}

	/**
	 * @param returnable whether the proxy should answer with a RESULT
	 * @return the request id the reply will carry, or 0 when there was no live
	 *         session. Ids start at 1, so 0 is never a real one.
	 */
	long sendMutation(MutationMode mode, String name, String type, byte[] value,
			String expectedType, byte[] expectedValue, String display, boolean returnable) {
		BlockingQueue<Frame> queue = outbound;
		if (state != SyncState.READY || queue == null)
			return 0;

		long requestId = requestIds.incrementAndGet();
		queue.add(new PacketOut(Protocol.MUTATE)
				.int64(requestId)
				.varInt(mode.id())
				.string(name)
				.nullableString(type)
				.nullableBytes(value)
				.nullableString(expectedType)
				.nullableBytes(expectedValue)
				.bool(returnable)
				.nullableString(display)
				.frame());
		return requestId;
	}

	private void connectLoop() {
		while (running) {
			try {
				session();
			} catch (IOException e) {
				lastError = describe(e);
				if (state == SyncState.READY || state == SyncState.SYNCING)
					plugin.getLogger().warning("lost the proxy: " + describe(e)
							+ " - reads still work from the mirror, writes are refused until it is back");
				else
					plugin.getLogger().info("proxy at " + describeTarget() + " unreachable ("
							+ describe(e) + "), retrying in " + (retryDelay / 1000) + "s");
			} catch (RuntimeException e) {
				lastError = e.toString();
				plugin.getLogger().severe("connection failed: " + e);
			} finally {
				boolean wasReady = state == SyncState.READY;
				state = SyncState.DISCONNECTED;
				if (wasReady)
					plugin.onDisconnected();
				// whatever is queued belongs to the dead session, and the reconnect
				// brings a fresh snapshot anyway
				inbound.clear();
				DeltaApplier applier = plugin.applier();
				if (applier != null)
					applier.sessionEnded();
				closeSocket();
			}

			if (!running)
				return;
			try {
				Thread.sleep(retryDelay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS);
		}
	}

	private void session() throws IOException {
		state = SyncState.CONNECTING;

		Socket connection = new Socket();
		connection.setTcpNoDelay(true);
		connection.connect(new InetSocketAddress(host, port), 5_000);
		socket = connection;

		DataInputStream in = new DataInputStream(connection.getInputStream());
		DataOutputStream out = new DataOutputStream(connection.getOutputStream());

		// asking to resume would keep writes the proxy refused while we were away.
		// only a full snapshot puts those right.
		DeltaApplier applier = plugin.applier();
		long resumeFrom = applier != null && applier.needsFullSnapshot() ? 0 : lastSeq;

		new PacketOut(Protocol.HELLO)
				.string(serverName)
				.string(token)
				.varInt(Protocol.VERSION)
				.string(plugin.skriptVersion())
				.int64(resumeFrom)
				.int64(plugin.manifestVersion())
				.frame()
				.write(out);

		Frame reply = Frame.read(in);
		if (reply.opcode == Protocol.REJECT) {
			String reason = reply.reader().string();
			lastError = reason;
			plugin.getLogger().severe("proxy rejected this server: " + reason);
			throw new IOException("rejected: " + reason);
		}
		if (reply.opcode != Protocol.WELCOME)
			throw new IOException("expected WELCOME, got opcode 0x" + Integer.toHexString(reply.opcode & 0xFF));

		BlockingQueue<Frame> queue = new LinkedBlockingQueue<>();
		outbound = queue;
		retryDelay = RETRY_MIN_MS;

		PacketIn welcome = reply.reader();
		long proxySeq = welcome.int64();
		boolean resuming = welcome.bool();

		state = SyncState.SYNCING;
		plugin.getLogger().info("connected to the proxy at " + describeTarget() + ", "
				+ (resuming ? "resuming from seq " + resumeFrom : "pulling a full snapshot")
				+ " (proxy is at seq " + proxySeq + ")");

		Thread writer = new Thread(() -> writerLoop(queue, out, connection), "skNetwork-client-writer");
		writer.setDaemon(true);
		writer.start();

		try {
			while (running) {
				inbound.add(Frame.read(in));
			}
		} finally {
			outbound = null;
			queue.add(POISON);
			// a write can still slip in behind the poison from Skript's thread, because it
			// checked the state a moment before it flipped. the writer never sees those.
			// draining may take the poison too, so a second one keeps the writer from
			// parking on this queue for good
			markUnsent(queue);
			queue.add(POISON);
		}
	}

	private void writerLoop(BlockingQueue<Frame> queue, DataOutputStream out, Socket connection) {
		Frame frame = null;
		try {
			while (true) {
				frame = queue.take();
				if (frame == POISON)
					return;
				frame.write(out);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			lastError = describe(e);
			// this one and whatever is queued behind it never reached the proxy. Skript has
			// already put the sets and deletes among them in its own map, so they are marked
			// for the full snapshot that puts this server back in step
			markUnsent(frame);
			markUnsent(queue);
			// this session's socket, never whichever one happens to be current
			try {
				connection.close();
			} catch (IOException ignored) {
			}
		}
	}

	private void markUnsent(BlockingQueue<Frame> queue) {
		List<Frame> left = new ArrayList<>();
		queue.drainTo(left);
		for (Frame frame : left)
			markUnsent(frame);
	}

	/** Only a plain set or delete has a local copy to repair; an atomic never touched Skript's map. */
	private void markUnsent(Frame frame) {
		if (frame == null || frame == POISON || frame.opcode != Protocol.MUTATE)
			return;
		DeltaApplier applier = plugin.applier();
		if (applier == null)
			return;
		try {
			PacketIn packet = frame.reader();
			packet.int64();
			MutationMode mode = MutationMode.byId((byte) packet.varInt());
			if (mode == MutationMode.SET || mode == MutationMode.DELETE)
				applier.markUnsynced(SkriptBridge.normalize(plugin.prefix() + packet.string()));
		} catch (IOException | RuntimeException ignored) {
			// our own frame, so this cannot happen; and if it did, a resume is the worst case
		}
	}

	/** A socket closed under us throws with no message, which used to read as "null". */
	private static String describe(IOException e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}

	private void closeSocket() {
		Socket open = socket;
		socket = null;
		if (open != null) {
			try {
				open.close();
			} catch (IOException ignored) {
			}
		}
	}

}
