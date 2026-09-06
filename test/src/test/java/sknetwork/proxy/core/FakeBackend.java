package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import sknetwork.common.Frame;
import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import sknetwork.common.PlayerAction;
import sknetwork.common.RemoteServer;
import sknetwork.common.PacketIn;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;

final class FakeBackend implements AutoCloseable {

	static final long TIMEOUT_MS = 5_000;

	private final String name;
	private final Socket socket;
	private final DataInputStream in;
	private final DataOutputStream out;
	private final AtomicLong requestIds = new AtomicLong();

	private long syncedSeq;
	private boolean resumed;

	FakeBackend(String name, int port, String token) throws IOException {
		this(name, port, token, Protocol.VERSION, 0, 0);
	}

	FakeBackend(String name, int port, String token, int protocol, long lastSeq, long manifestVersion)
			throws IOException {
		this(name, port, token, protocol, lastSeq, manifestVersion, true);
	}

	FakeBackend(String name, int port, String token, int protocol, long lastSeq,
			long manifestVersion, boolean usePlayerUuids) throws IOException {
		this.name = name;
		this.socket = new Socket();
		socket.connect(new InetSocketAddress("127.0.0.1", port), (int) TIMEOUT_MS);
		socket.setTcpNoDelay(true);
		socket.setSoTimeout((int) TIMEOUT_MS);
		this.in = new DataInputStream(socket.getInputStream());
		this.out = new DataOutputStream(socket.getOutputStream());

		new PacketOut(Protocol.HELLO)
				.string(name)
				.string(token)
				.varInt(protocol)
				.string("2.16.2")
				.int64(lastSeq)
				.int64(manifestVersion)
				.bool(usePlayerUuids)
				.frame()
				.write(out);
	}

	String name() {
		return name;
	}

	long syncedSeq() {
		return syncedSeq;
	}

	boolean resumed() {
		return resumed;
	}

	Sync sync() throws IOException {
		PacketIn welcome = await(Protocol.WELCOME).reader();
		welcome.int64();
		resumed = welcome.bool();

		List<String> snapshot = new ArrayList<>();
		List<Delta> replayed = new ArrayList<>();

		while (true) {
			Frame frame = take();
			assertNotNull(frame, name + " never finished syncing");

			switch (frame.opcode) {
				case Protocol.SNAPSHOT -> {
					PacketIn packet = frame.reader();
					int count = packet.varInt();
					for (int i = 0; i < count; i++) {
						snapshot.add(packet.string());
						packet.nullableString();
						packet.nullableBytes();
					}
				}
				case Protocol.DELTA -> {
					PacketIn packet = frame.reader();
					replayed.add(new Delta(packet.int64(), MutationMode.byId((byte) packet.varInt()),
							packet.string(), packet.nullableString(), packet.nullableBytes(),
							packet.nullableString(), packet.nullableBytes()));
				}
				case Protocol.SYNCED -> {
					PacketIn packet = frame.reader();
					syncedSeq = packet.int64();
					return new Sync(resumed, packet.bool(), syncedSeq, snapshot, replayed);
				}
				default -> {
				}
			}
		}
	}

	long mutate(MutationMode mode, String variable, String type, byte[] value) throws IOException {
		return mutate(mode, variable, type, value, null, null, true);
	}

	long fireAndForget(MutationMode mode, String variable, String type, byte[] value) throws IOException {
		return mutate(mode, variable, type, value, null, null, false);
	}

	long mutate(MutationMode mode, String variable, String type, byte[] value,
			String expectedType, byte[] expectedValue, boolean returnable) throws IOException {
		return mutate(mode, variable, type, value, expectedType, expectedValue, returnable,
				value == null ? null : "shown");
	}

	long mutate(MutationMode mode, String variable, String type, byte[] value,
			String expectedType, byte[] expectedValue, boolean returnable, String display)
			throws IOException {
		long requestId = requestIds.incrementAndGet();
		new PacketOut(Protocol.MUTATE)
				.int64(requestId)
				.varInt(mode.id())
				.string(variable)
				.nullableString(type)
				.nullableBytes(value)
				.nullableString(expectedType)
				.nullableBytes(expectedValue)
				.bool(returnable)
				.nullableString(display)
				.frame()
				.write(out);
		return requestId;
	}

	Result result(long requestId) throws IOException {
		while (true) {
			Frame frame = await(Protocol.RESULT);
			PacketIn packet = frame.reader();
			long id = packet.int64();
			boolean ok = packet.bool();
			long seq = packet.int64();
			String type = packet.nullableString();
			byte[] value = packet.nullableBytes();
			String error = packet.nullableString();

			if (id == requestId)
				return new Result(ok, seq, type, value, error);
		}
	}

	Result set(String variable, String type, byte[] value) throws IOException {
		return result(mutate(MutationMode.SET, variable, type, value));
	}

	Delta delta() throws IOException {
		PacketIn packet = await(Protocol.DELTA).reader();
		return new Delta(packet.int64(), MutationMode.byId((byte) packet.varInt()), packet.string(),
				packet.nullableString(), packet.nullableBytes(),
				packet.nullableString(), packet.nullableBytes());
	}

	Delta deltaAt(long seq) throws IOException {
		while (true) {
			Delta delta = delta();
			if (delta.seq() >= seq)
				return delta;
		}
	}

	void sendServerInfo(RemoteServer info) throws IOException {
		PacketOut out = new PacketOut(Protocol.SERVER_INFO);
		info.write(out);
		out.frame().write(this.out);
	}

	List<RemoteServer> networkState() throws IOException {
		PacketIn packet = await(Protocol.NETWORK_STATE).reader();
		int count = packet.varInt();
		List<RemoteServer> servers = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			servers.add(RemoteServer.read(packet));
		return servers;
	}

	void playerAction(PlayerAction action, List<String> targets, String payload) throws IOException {
		PacketOut out = new PacketOut(Protocol.PLAYER_ACTION)
				.varInt(action.id())
				.varInt(targets.size());
		targets.forEach(out::string);
		out.string(payload).frame().write(this.out);
	}

	Delivery delivery() throws IOException {
		PacketIn packet = await(Protocol.PLAYER_DELIVERY).reader();
		PlayerAction action = PlayerAction.byId((byte) packet.varInt());
		int count = packet.varInt();
		List<String> targets = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			targets.add(packet.string());
		return new Delivery(action, targets, packet.string());
	}

	void consoleCommand(List<String> servers, String command) throws IOException {
		PacketOut out = new PacketOut(Protocol.CONSOLE_COMMAND).varInt(servers.size());
		servers.forEach(out::string);
		out.string(command).frame().write(this.out);
	}

	String awaitConsoleCommand() throws IOException {
		return await(Protocol.CONSOLE_COMMAND).reader().string();
	}

	long ping(long nonce) throws IOException {
		new PacketOut(Protocol.PING).int64(nonce).frame().write(out);
		return await(Protocol.PONG).reader().int64();
	}

	Manifest manifest() throws IOException {
		return Manifest.read(await(Protocol.MANIFEST).reader());
	}

	void fetch(long version, List<String> paths) throws IOException {
		PacketOut packet = new PacketOut(Protocol.FETCH).int64(version).varInt(paths.size());
		for (String path : paths)
			packet.string(path);
		packet.frame().write(out);
	}

	byte[] file(String path) throws IOException {
		while (true) {
			PacketIn packet = await(Protocol.FILE).reader();
			packet.int64();
			String received = packet.string();
			byte[] content = packet.nullableBytes();
			if (received.equals(path))
				return content;
		}
	}

	void reportLoad(long version, int loaded, List<String> errors) throws IOException {
		PacketOut packet = new PacketOut(Protocol.LOAD_RESULT)
				.int64(version)
				.varInt(loaded)
				.varInt(errors.size());
		for (String error : errors)
			packet.string("global/a.sk").varInt(3).string(error).bool(true);
		packet.frame().write(out);
	}

	String rejection() throws IOException {
		return await(Protocol.REJECT).reader().string();
	}

	void sendRaw(Frame frame) throws IOException {
		frame.write(out);
	}

	Frame take() throws IOException {
		try {
			return Frame.read(in);
		} catch (SocketTimeoutException e) {
			return null;
		}
	}

	/** @return the next frame, or null if none arrives within {@code timeoutMs} */
	Frame take(long timeoutMs) throws IOException {
		socket.setSoTimeout((int) timeoutMs);
		try {
			return take();
		} finally {
			socket.setSoTimeout((int) TIMEOUT_MS);
		}
	}

	/**
	 * Every NETWORK_STATE frame that arrives before the line goes quiet, so a test
	 * can look at the last one a backend was left holding.
	 */
	List<List<RemoteServer>> statesUntilQuiet(long quietMs) throws IOException {
		List<List<RemoteServer>> seen = new ArrayList<>();
		while (true) {
			Frame frame = take(quietMs);
			if (frame == null)
				return seen;
			if (frame.opcode != Protocol.NETWORK_STATE)
				continue;
			PacketIn packet = frame.reader();
			int count = packet.varInt();
			List<RemoteServer> servers = new ArrayList<>(count);
			for (int i = 0; i < count; i++)
				servers.add(RemoteServer.read(packet));
			seen.add(servers);
		}
	}

	Frame await(byte opcode) throws IOException {
		while (true) {
			Frame frame = take();
			assertNotNull(frame, name + " waited " + TIMEOUT_MS + "ms for opcode 0x"
					+ Integer.toHexString(opcode & 0xFF) + " and got nothing");
			if (frame.opcode == opcode)
				return frame;
		}
	}

	@Override
	public void close() {
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	record Result(boolean ok, long seq, String type, byte[] value, String error) {

		long asLong() {
			assertEquals("long", type);
			return Numbers.readLong(type, value);
		}

		double asDouble() {
			return Numbers.readDouble(type, value);
		}
	}

	record Delta(long seq, MutationMode mode, String name, String type, byte[] value,
			String wasType, byte[] wasValue) {
	}

	record Delivery(PlayerAction action, List<String> targets, String payload) {
	}

	record Sync(boolean resumed, boolean fullSnapshot, long seq, List<String> snapshot,
			List<Delta> replayed) {
	}
}
