package sknetwork.proxy.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import java.util.ArrayList;
import java.util.List;

import sknetwork.common.Frame;
import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import sknetwork.common.PacketIn;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerAction;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;

final class BackendConnection {

	private static final Frame POISON = new Frame((byte) 0, new byte[0]);

	private final NetworkServer server;
	private final Socket socket;
	private final BlockingQueue<Frame> outbound = new LinkedBlockingQueue<>();
	private final AtomicLong queuedBytes = new AtomicLong();
	private final AtomicBoolean closed = new AtomicBoolean();

	private final String address;
	private volatile String name;
	private volatile boolean ready;
	private volatile long lastSeq;
	private volatile long manifestVersion;
	private DataInputStream in;
	private DataOutputStream out;

	BackendConnection(NetworkServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
		this.address = String.valueOf(socket.getRemoteSocketAddress());
		this.name = address;
	}

	String name() {
		return name;
	}

	String address() {
		return address;
	}

	boolean isReady() {
		return ready;
	}

	void markReady() {
		ready = true;
	}

	void start() {
		Thread reader = new Thread(this::readerLoop, "skNetwork-reader-" + name);
		reader.setDaemon(true);
		reader.start();
	}

	void send(Frame frame) {
		if (closed.get())
			return;
		outbound.add(frame);

		long waiting = queuedBytes.addAndGet(frame.payload.length);
		if (waiting > server.backlogLimit() && !closed.get()) {
			server.log().warn(name + " is not reading what the proxy sends: " + (waiting >> 20)
					+ " MB is waiting for it, so it is being dropped. It will reconnect and "
					+ "catch up on its own.");
			close("dropped for not keeping up");
		}
	}

	private void readerLoop() {
		try {
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());

			if (!handshake())
				return;

			Thread writer = new Thread(this::writerLoop, "skNetwork-writer-" + name);
			writer.setDaemon(true);
			writer.start();

			server.register(this);
			server.submitSync(this, lastSeq);

			while (!closed.get()) {
				Frame frame = Frame.read(in);
				handle(frame);
			}
		} catch (IOException e) {
			close(Frame.isCleanClose(e) ? "disconnected" : "read failed: " + e.getMessage());
		} catch (RuntimeException e) {
			server.log().error("connection " + name + " failed", e);
			close("internal error");
		}
	}

	/** @return true when the backend is accepted and may proceed. */
	private boolean handshake() throws IOException {
		Frame frame = Frame.read(in);
		if (frame.opcode != Protocol.HELLO) {
			reject("expected HELLO, got opcode 0x" + Integer.toHexString(frame.opcode & 0xFF));
			return false;
		}

		PacketIn packet = frame.reader();
		String serverName = packet.string();
		String token = packet.string();
		int protocol = packet.varInt();
		String skriptVersion = packet.string();
		long lastSeq = packet.int64();
		long manifestVersion = packet.int64();

		this.name = serverName;

		if (protocol != Protocol.VERSION) {
			reject("protocol mismatch: this proxy speaks " + Protocol.VERSION + ", "
					+ serverName + " speaks " + protocol + ". Both halves come from the same jar, "
					+ "so update whichever one is behind.");
			return false;
		}
		if (!server.token().equals(token)) {
			reject("bad token - check 'proxy.token' in " + serverName + "'s config.yml "
					+ "against 'token' in the proxy's");
			return false;
		}

		server.log().info(serverName + " connected (Skript " + skriptVersion
				+ ", lastSeq " + lastSeq + ")");
		this.lastSeq = lastSeq;
		this.manifestVersion = manifestVersion;
		return true;
	}

	long manifestVersion() {
		return manifestVersion;
	}

	void sendManifest(Manifest manifest) {
		manifestVersion = manifest.version();
		PacketOut out = new PacketOut(Protocol.MANIFEST);
		manifest.write(out);
		send(out.frame());
	}

	/** Sent from the writer thread, so the decision and the frames stay in one order. */
	void sendWelcome(long currentSeq, boolean resuming) {
		send(new PacketOut(Protocol.WELCOME).int64(currentSeq).bool(resuming).frame());
	}

	private void handle(Frame frame) throws IOException {
		PacketIn packet = frame.reader();

		switch (frame.opcode) {
			case Protocol.MUTATE -> server.submitMutation(this, new Mutation(
					packet.int64(),
					MutationMode.byId((byte) packet.varInt()),
					packet.string(),
					packet.nullableString(),
					packet.nullableBytes(),
					packet.nullableString(),
					packet.nullableBytes(),
					packet.bool(),
					packet.nullableString()));
			case Protocol.PING -> send(new PacketOut(Protocol.PONG).int64(packet.int64()).frame());
			case Protocol.FETCH -> sendFiles(packet);
			case Protocol.LOAD_RESULT -> reportLoad(packet);
			case Protocol.SERVER_INFO -> server.serverInfo(this, RemoteServer.read(packet));
			case Protocol.PLAYER_ACTION -> playerAction(packet);
			case Protocol.CONSOLE_COMMAND -> consoleCommand(packet);
			default -> server.log().warn("ignoring unexpected opcode 0x"
					+ Integer.toHexString(frame.opcode & 0xFF) + " from " + name);
		}
	}

	private void sendFiles(PacketIn packet) throws IOException {
		long version = packet.int64();
		int count = packet.varInt();
		for (int i = 0; i < count; i++) {
			String path = packet.string();
			byte[] content = server.scripts().content(path);
			// a null body means it went away between the manifest and the fetch, and
			// the backend should wait for the next manifest rather than half apply this one
			send(new PacketOut(Protocol.FILE).int64(version).string(path)
					.nullableBytes(content).frame());
		}
	}

	private void playerAction(PacketIn packet) throws IOException {
		PlayerAction action = PlayerAction.byId((byte) packet.varInt());
		server.playerAction(this, action, names(packet), packet.string());
	}

	private void consoleCommand(PacketIn packet) throws IOException {
		server.consoleCommand(this, names(packet), packet.string());
	}

	private static List<String> names(PacketIn packet) throws IOException {
		int count = packet.varInt();
		if (count < 0 || count > 100_000)
			throw new IOException("count " + count + " is out of range");

		List<String> names = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			names.add(packet.string());
		return names;
	}

	private void reportLoad(PacketIn packet) throws IOException {
		long version = packet.int64();
		int loaded = packet.varInt();
		int count = packet.varInt();

		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String path = packet.string();
			int line = packet.varInt();
			String message = packet.string();
			boolean severe = packet.bool();
			String where = path.isEmpty() ? "" : path + (line > 0 ? ":" + line : "") + " ";
			(severe ? errors : warnings).add(where + message);
		}
		server.reportLoad(name, version, loaded, errors, warnings);
	}

	private void writerLoop() {
		try {
			while (!closed.get()) {
				Frame frame = outbound.take();
				if (frame == POISON)
					return;
				frame.write(out);
				queuedBytes.addAndGet(-frame.payload.length);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			close("write failed: " + e.getMessage());
		}
	}

	private void reject(String reason) {
		try {
			new PacketOut(Protocol.REJECT).string(reason).frame().write(out);
		} catch (IOException ignored) {
		}
		server.log().warn("rejected " + name + ": " + reason);
		// close() logs the reason too, and a backend retrying a version mismatch every
		// thirty seconds does not need each one printed twice
		close("rejected");
	}

	/** Only answered when the caller asked for one. */
	void reply(Mutation mutation, Outcome outcome, long seq) {
		if (!mutation.returnable())
			return;

		send(new PacketOut(Protocol.RESULT)
				.int64(mutation.requestId())
				.bool(outcome.applied())
				.int64(seq)
				.nullableString(outcome.applied() && !outcome.delete() ? outcome.type() : null)
				.nullableBytes(outcome.applied() && !outcome.delete() ? outcome.value() : null)
				.nullableString(outcome.error())
				.frame());
	}

	void close(String reason) {
		if (!closed.compareAndSet(false, true))
			return;

		ready = false;
		server.unregister(this);
		outbound.add(POISON);
		try {
			socket.close();
		} catch (IOException ignored) {
		}
		server.log().info(name + " " + reason);
	}
}
