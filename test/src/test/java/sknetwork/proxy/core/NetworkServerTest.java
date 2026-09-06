package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sknetwork.common.Frame;
import sknetwork.common.Manifest;
import sknetwork.common.MutationMode;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;

class NetworkServerTest {

	private static final String TOKEN = "a-test-token";

	@TempDir
	File folder;

	private RecordingLog log;
	private NetworkServer server;
	private int port;
	private final List<FakeBackend> backends = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		log = new RecordingLog();
		server = start(null, 10_000);
	}

	@AfterEach
	void tearDown() {
		backends.forEach(FakeBackend::close);
		backends.clear();
		if (server != null)
			server.stop();
	}

	@Test
	void rejectsABackendWithTheWrongToken() throws IOException {
		FakeBackend lobby = connect("lobby", "not-the-token", Protocol.VERSION, 0);

		assertTrue(lobby.rejection().contains("bad token"));
		awaitWarning("bad token");
	}

	@Test
	void rejectsABackendSpeakingAnotherProtocol() throws IOException {
		FakeBackend lobby = connect("lobby", TOKEN, Protocol.VERSION + 1, 0);

		assertTrue(lobby.rejection().contains("protocol mismatch"));
	}

	@Test
	void warnsAboutABackendKeyingPlayersTheOtherWay() throws IOException {
		server.usePlayerUuids(true);

		connectWithPlayerUuids("survival", false).sync();

		awaitWarning("use player UUIDs in variable names");
	}

	/** Naming the odd one out is the whole point, so the message has to carry it. */
	@Test
	void namesTheBackendThatDisagrees() throws IOException {
		server.usePlayerUuids(true);

		connectWithPlayerUuids("survival", false).sync();

		awaitWarning("survival has 'use player UUIDs in variable names' set to false");
	}

	/** A mismatch is worth saying out loud, but it is not worth refusing the backend. */
	@Test
	void stillAcceptsABackendKeyingPlayersTheOtherWay() throws IOException {
		server.usePlayerUuids(true);

		FakeBackend survival = connectWithPlayerUuids("survival", false);

		assertEquals(0, survival.sync().seq());
	}

	@Test
	void staysQuietWhenABackendAgreesWithTheProxy() throws IOException {
		server.usePlayerUuids(false);

		connectWithPlayerUuids("lobby", false).sync();

		assertFalse(log.sawWarning("use player UUIDs in variable names"));
	}

	@Test
	void rejectsAnythingThatIsNotAHandshake() throws IOException {
		try (Socket rude = new Socket("127.0.0.1", port)) {
			rude.setSoTimeout(5_000);
			new PacketOut(Protocol.PING).int64(1).frame().write(new DataOutputStream(rude.getOutputStream()));

			Frame reply = Frame.read(new DataInputStream(rude.getInputStream()));

			assertEquals(Protocol.REJECT, reply.opcode);
			assertTrue(reply.reader().string().contains("expected HELLO"));
		}
	}

	@Test
	void syncsAFreshBackendWithNothing() throws IOException {
		FakeBackend.Sync sync = connected("lobby").sync();

		assertFalse(sync.resumed());
		assertTrue(sync.fullSnapshot());
		assertEquals(0, sync.seq());
		assertTrue(sync.snapshot().isEmpty());
	}

	@Test
	void storesASetAndBroadcastsIt() throws IOException {
		FakeBackend lobby = synced("lobby");

		long request = lobby.mutate(MutationMode.SET, "coins", "long", Numbers.writeLong(100));
		FakeBackend.Delta delta = lobby.delta();
		FakeBackend.Result result = lobby.result(request);

		assertEquals(1, delta.seq());
		assertEquals(MutationMode.SET, delta.mode());
		assertEquals("coins", delta.name());
		assertTrue(result.ok());
		assertEquals(100, result.asLong());
		assertEquals(1, server.variableCount());
		assertEquals(1, server.sequence());
	}

	@Test
	void carriesThePreviousValueInEveryDelta() throws IOException {
		FakeBackend lobby = synced("lobby");

		lobby.fireAndForget(MutationMode.SET, "coins", "long", Numbers.writeLong(100));
		FakeBackend.Delta first = lobby.delta();

		lobby.fireAndForget(MutationMode.SET, "coins", "long", Numbers.writeLong(250));
		FakeBackend.Delta second = lobby.delta();

		assertNull(first.wasValue());
		assertEquals(100, Numbers.readLong(second.wasType(), second.wasValue()));
		assertEquals(250, Numbers.readLong(second.type(), second.value()));
	}

	@Test
	void carriesThePreviousValueWhenAKeyIsDeleted() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(7));

		lobby.fireAndForget(MutationMode.DELETE, "coins", null, null);
		FakeBackend.Delta delta = lobby.delta();

		assertEquals(MutationMode.DELETE, delta.mode());
		assertNull(delta.value());
		assertEquals(7, Numbers.readLong(delta.wasType(), delta.wasValue()));
	}

	@Test
	void sendsAWriteToEveryOtherBackend() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		lobby.fireAndForget(MutationMode.SET, "coins", "long", Numbers.writeLong(100));

		assertEquals(100, Numbers.readLong("long", survival.delta().value()));
		assertEquals(100, Numbers.readLong("long", lobby.delta().value()));
	}

	@Test
	void handsANewBackendEverythingItMissed() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));
		lobby.set("name", "string", new byte[] {1, 2});

		FakeBackend.Sync sync = connected("survival").sync();

		assertEquals(2, sync.snapshot().size());
		assertTrue(sync.snapshot().contains("coins"));
		assertTrue(sync.snapshot().contains("name"));
		assertEquals(2, sync.seq());
	}

	@Test
	void addsWithoutTheBackendDoingTheArithmetic() throws IOException {
		FakeBackend lobby = synced("lobby");

		assertEquals(5, lobby.result(lobby.mutate(MutationMode.ADD, "coins", "long",
				Numbers.writeLong(5))).asLong());
		assertEquals(12, lobby.result(lobby.mutate(MutationMode.ADD, "coins", "long",
				Numbers.writeLong(7))).asLong());
		assertEquals(2, lobby.result(lobby.mutate(MutationMode.REMOVE, "coins", "long",
				Numbers.writeLong(10))).asLong());
	}

	@Test
	void goesNegativeWithoutAFloor() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));

		assertEquals(-400, lobby.result(lobby.mutate(MutationMode.REMOVE, "coins", "long",
				Numbers.writeLong(500))).asLong());
	}

	@Test
	void staysExactPastTheDoublePrecisionLimit() throws IOException {
		FakeBackend lobby = synced("lobby");
		long big = 1L << 53;
		lobby.set("coins", "long", Numbers.writeLong(big));

		assertEquals(big + 1, lobby.result(lobby.mutate(MutationMode.ADD, "coins", "long",
				Numbers.writeLong(1))).asLong());
	}

	@Test
	void turnsFractionalArithmeticIntoADouble() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(1));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.ADD, "coins", "double",
				Numbers.writeDouble(0.5)));

		assertEquals("double", result.type());
		assertEquals(1.5, result.asDouble());
	}

	@Test
	void refusesToAddToSomethingThatIsNotANumber() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("name", "string", new byte[] {1, 2, 3});

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.ADD, "name", "long",
				Numbers.writeLong(1)));

		assertFalse(result.ok());
		assertTrue(result.error().contains("holds a string"));
	}

	@Test
	void refusesToAddSomethingThatIsNotANumber() throws IOException {
		FakeBackend lobby = synced("lobby");

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.ADD, "coins", "string",
				new byte[] {1}));

		assertFalse(result.ok());
		assertTrue(result.error().contains("can only add numbers"));
	}

	@Test
	void refusesAnOverflow() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(Long.MAX_VALUE));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.ADD, "coins", "long",
				Numbers.writeLong(1)));

		assertFalse(result.ok());
		assertTrue(result.error().contains("does not fit"));
	}

	@Test
	void refusesToSpendBelowTheFloor() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.REMOVE_IF_ABOVE, "coins",
				"long", Numbers.writeLong(250), "long", Numbers.writeLong(0), true));

		assertFalse(result.ok());
		assertTrue(result.error().contains("below the floor"));
		assertEquals(1, server.sequence());
	}

	@Test
	void allowsSpendingTheWholeBalance() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.REMOVE_IF_ABOVE, "coins",
				"long", Numbers.writeLong(100), "long", Numbers.writeLong(0), true));

		assertTrue(result.ok());
		assertEquals(0, result.asLong());
	}

	@Test
	void refusesAFloorThatIsNotANumber() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.REMOVE_IF_ABOVE, "coins",
				"long", Numbers.writeLong(10), null, null, true));

		assertFalse(result.ok());
		assertTrue(result.error().contains("floor is not a number"));
	}

	@Test
	void letsOnlyOneOfTwoRacingSpendsThrough() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");
		lobby.set("coins", "long", Numbers.writeLong(100));

		long first = lobby.mutate(MutationMode.REMOVE_IF_ABOVE, "coins", "long",
				Numbers.writeLong(100), "long", Numbers.writeLong(0), true);
		long second = survival.mutate(MutationMode.REMOVE_IF_ABOVE, "coins", "long",
				Numbers.writeLong(100), "long", Numbers.writeLong(0), true);

		boolean lobbyWon = lobby.result(first).ok();
		boolean survivalWon = survival.result(second).ok();

		assertTrue(lobbyWon ^ survivalWon);
	}

	@Test
	void losesNothingWhenTwoBackendsAddAtOnce() throws IOException {
		FakeBackend lobby = synced("lobby");
		FakeBackend survival = synced("survival");

		for (int i = 0; i < 100; i++) {
			lobby.fireAndForget(MutationMode.ADD, "coins", "long", Numbers.writeLong(1));
			survival.fireAndForget(MutationMode.ADD, "coins", "long", Numbers.writeLong(1));
		}
		lobby.deltaAt(200);

		assertEquals(200, lobby.result(lobby.mutate(MutationMode.ADD, "coins", "long",
				Numbers.writeLong(0))).asLong());
	}

	@Test
	void setsOnlyOnceWhenTheKeyIsAbsent() throws IOException {
		FakeBackend lobby = synced("lobby");

		FakeBackend.Result first = lobby.result(lobby.mutate(MutationMode.SET_IF_ABSENT, "owner",
				"string", new byte[] {1}));
		FakeBackend.Result second = lobby.result(lobby.mutate(MutationMode.SET_IF_ABSENT, "owner",
				"string", new byte[] {2}));

		assertTrue(first.ok());
		assertFalse(second.ok());
		assertTrue(second.error().contains("already set"));
	}

	@Test
	void swapsAValueOnlyWhenItIsTheExpectedOne() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));

		FakeBackend.Result matched = lobby.result(lobby.mutate(MutationMode.COMPARE_AND_SET, "coins",
				"long", Numbers.writeLong(200), "long", Numbers.writeLong(100), true));
		FakeBackend.Result stale = lobby.result(lobby.mutate(MutationMode.COMPARE_AND_SET, "coins",
				"long", Numbers.writeLong(300), "long", Numbers.writeLong(100), true));

		assertTrue(matched.ok());
		assertEquals(200, matched.asLong());
		assertFalse(stale.ok());
		assertTrue(stale.error().contains("does not match"));
	}

	@Test
	void treatsAnAbsentKeyAsMatchingNothing() throws IOException {
		FakeBackend lobby = synced("lobby");

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.COMPARE_AND_SET, "coins",
				"long", Numbers.writeLong(1), null, null, true));

		assertTrue(result.ok());
		assertEquals(1, result.asLong());
	}

	@Test
	void refusesToStoreAnythingUnderAListName() throws IOException {
		FakeBackend lobby = synced("lobby");

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.SET, "coins::*", "long",
				Numbers.writeLong(1)));

		assertFalse(result.ok());
		assertTrue(result.error().contains("can only be deleted"));
		assertEquals(0, server.variableCount());
	}

	@Test
	void deletesAWholeBranch() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins::eult", "long", Numbers.writeLong(1));
		lobby.set("coins::njol", "long", Numbers.writeLong(2));
		lobby.set("party::5", "long", Numbers.writeLong(3));

		assertTrue(lobby.result(lobby.mutate(MutationMode.DELETE, "coins::*", null, null)).ok());
		assertEquals(1, server.variableCount());
		assertEquals(List.of("party::5"), connected("survival").sync().snapshot());
	}

	@Test
	void spendsNoSequenceNumberDeletingWhatIsNotThere() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(1));

		FakeBackend.Result result = lobby.result(lobby.mutate(MutationMode.DELETE, "nothing", null, null));

		assertTrue(result.ok());
		assertEquals(1, server.sequence());
	}

	@Test
	void answersAPing() throws IOException {
		FakeBackend lobby = synced("lobby");

		assertEquals(1_234_567, lobby.ping(1_234_567));
	}

	@Test
	void ignoresAnOpcodeItDoesNotKnow() throws IOException {
		FakeBackend lobby = synced("lobby");

		lobby.sendRaw(new Frame((byte) 0x7F, new byte[] {1, 2, 3}));

		assertEquals(99, lobby.ping(99));
		awaitWarning("unexpected opcode");
	}

	@Test
	void warnsWhenTwoBackendsShareAName() throws IOException {
		synced("lobby");
		synced("lobby");

		awaitWarning("Two servers sharing a name");
	}

	@Test
	void countsWhatIsConnected() throws IOException {
		synced("lobby");
		synced("survival");

		assertEquals(2, server.connectionCount());
	}

	@Test
	void replaysWhatABackendMissedRatherThanResending() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("a", "long", Numbers.writeLong(1));
		lobby.set("b", "long", Numbers.writeLong(2));
		lobby.set("c", "long", Numbers.writeLong(3));

		FakeBackend.Sync sync = connect("lobby", TOKEN, Protocol.VERSION, 1).sync();

		assertTrue(sync.resumed());
		assertFalse(sync.fullSnapshot());
		assertTrue(sync.snapshot().isEmpty());
		assertEquals(2, sync.replayed().size());
		assertEquals(List.of("b", "c"), sync.replayed().stream().map(FakeBackend.Delta::name).toList());
		assertEquals(3, sync.seq());
	}

	@Test
	void sendsAFullSnapshotWhenTheReplayBufferCannotCover() throws IOException {
		server.stop();
		server = start(null, 0);

		FakeBackend lobby = synced("lobby");
		lobby.set("a", "long", Numbers.writeLong(1));
		lobby.set("b", "long", Numbers.writeLong(2));

		FakeBackend.Sync sync = connect("lobby", TOKEN, Protocol.VERSION, 1).sync();

		assertFalse(sync.resumed());
		assertTrue(sync.fullSnapshot());
		assertEquals(2, sync.snapshot().size());
		awaitLog("past the replay buffer");
	}

	@Test
	void rebuildsABackendThatIsAheadOfTheProxy() throws IOException {
		FakeBackend.Sync sync = connect("lobby", TOKEN, Protocol.VERSION, 999).sync();

		assertFalse(sync.resumed());
		assertTrue(sync.fullSnapshot());
		awaitLog("past the replay buffer");
	}

	@Test
	void dropsABackendThatStopsReadingInsteadOfQueueingForever() throws IOException {
		server.backlogLimit(1024 * 1024);
		FakeBackend frozen = synced("frozen");
		FakeBackend lobby = synced("lobby");

		byte[] blob = new byte[200 * 1024];
		for (int i = 0; i < 100; i++)
			lobby.set("blob::" + i, "string", blob);

		awaitWarning("not reading what the proxy sends");
		await(() -> server.connectionCount() == 1, "the frozen backend was never dropped");
		assertEquals(1, lobby.ping(1));
		frozen.close();
	}

	@Test
	void keepsEverySnapshotFrameUnderTheCapWhenValuesAreLarge() throws IOException {
		FakeBackend lobby = synced("lobby");
		// forty values of 300 KB is 12 MB inside one window of 500 entries, past the
		// 8 MB a frame may carry. each one arrived in its own frame, so the proxy took it
		byte[] blob = new byte[300 * 1024];
		for (int i = 0; i < 40; i++)
			lobby.fireAndForget(MutationMode.SET, "blob::" + i, "string", blob);
		lobby.deltaAt(40);

		assertEquals(40, connected("survival").sync().snapshot().size());
	}

	@Test
	void spreadsALargeSnapshotOverSeveralFrames() throws IOException {
		FakeBackend lobby = synced("lobby");
		for (int i = 0; i < 1200; i++)
			lobby.fireAndForget(MutationMode.SET, "key::" + i, "long", Numbers.writeLong(i));
		lobby.deltaAt(1200);

		assertEquals(1200, connected("survival").sync().snapshot().size());
	}

	@Test
	void bringsEverythingBackAfterARestart() throws IOException {
		File logFile = new File(folder, "network.csv");
		server.stop();
		server = start(logFile, 10_000);

		FakeBackend lobby = synced("lobby");
		lobby.set("coins", "long", Numbers.writeLong(100));
		lobby.set("name", "string", new byte[] {1, 2});
		backends.forEach(FakeBackend::close);
		backends.clear();
		server.stop();

		server = start(logFile, 10_000);

		assertEquals(2, server.variableCount());
		assertEquals(2, server.sequence());
		assertEquals(2, connected("survival").sync().snapshot().size());
	}

	@Test
	void keepsCountingFromWhereTheLogLeftOff() throws IOException {
		File logFile = new File(folder, "network.csv");
		server.stop();
		server = start(logFile, 10_000);
		synced("lobby").set("coins", "long", Numbers.writeLong(100));
		backends.forEach(FakeBackend::close);
		backends.clear();
		server.stop();

		server = start(logFile, 10_000);
		FakeBackend lobby = synced("lobby");

		assertEquals(2, lobby.result(lobby.mutate(MutationMode.SET, "other", "long",
				Numbers.writeLong(1))).seq());
	}

	@Test
	void startsEmptyWhenNothingIsPersisted() throws IOException {
		synced("lobby").set("coins", "long", Numbers.writeLong(100));
		backends.forEach(FakeBackend::close);
		backends.clear();
		server.stop();

		server = start(null, 10_000);

		assertEquals(0, server.variableCount());
	}

	@Test
	void findsWhatADumpAsksFor() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.set("coins::eult", "long", Numbers.writeLong(100));
		lobby.set("coins::njol", "long", Numbers.writeLong(200));
		lobby.set("party::5", "long", Numbers.writeLong(1));

		NetworkServer.Dump dump = server.dump("coins::*", 10);

		assertEquals(2, dump.total());
		assertEquals("coins::eult", dump.lines().get(0).name());
		assertEquals("long", dump.lines().get(0).type());
		assertEquals("shown", dump.lines().get(0).value());
		assertEquals(1, dump.lines().get(0).seq());
	}

	@Test
	void countsEverythingItMatchedEvenPastTheLimit() throws IOException {
		FakeBackend lobby = synced("lobby");
		for (int i = 0; i < 10; i++)
			lobby.set("key::" + i, "long", Numbers.writeLong(i));

		NetworkServer.Dump dump = server.dump("key::*", 3);

		assertEquals(10, dump.total());
		assertEquals(3, dump.lines().size());
	}

	@Test
	void saysSoWhenAValueHasNoDisplay() throws IOException {
		FakeBackend lobby = synced("lobby");
		lobby.mutate(MutationMode.SET, "opaque", "itemtype", new byte[] {1, 2, 3}, null, null, false, null);
		lobby.delta();

		assertEquals("<3 bytes, no display>", server.dump("opaque", 10).lines().get(0).value());
	}

	@Test
	void pushesAManifestToABackendThatConnects() throws IOException {
		ScriptLibrary library = libraryWith("global/coins.sk", "on load:\n\tbroadcast \"hi\"");
		server.scripts(library);

		FakeBackend lobby = synced("lobby");
		Manifest manifest = lobby.manifest();

		assertEquals(1, manifest.size());
		assertEquals(library.version(), manifest.version());
		assertTrue(manifest.hashesByPath().containsKey("global/coins.sk"));
	}

	@Test
	void sendsTheBytesABackendFetches() throws IOException {
		ScriptLibrary library = libraryWith("global/coins.sk", "on load:\n\tbroadcast \"hi\"");
		server.scripts(library);

		FakeBackend lobby = synced("lobby");
		Manifest manifest = lobby.manifest();
		lobby.fetch(manifest.version(), List.of("global/coins.sk"));

		assertEquals("on load:\n\tbroadcast \"hi\"",
				new String(lobby.file("global/coins.sk"), StandardCharsets.UTF_8));
	}

	@Test
	void sendsNothingForAScriptThatWentAway() throws IOException {
		ScriptLibrary library = libraryWith("global/coins.sk", "on load:");
		server.scripts(library);

		FakeBackend lobby = synced("lobby");
		Manifest manifest = lobby.manifest();
		lobby.fetch(manifest.version(), List.of("global/gone.sk"));

		assertNull(lobby.file("global/gone.sk"));
	}

	@Test
	void printsWhatABackendReportsLoading() throws IOException {
		ScriptLibrary library = libraryWith("global/coins.sk", "on load:");
		server.scripts(library);

		FakeBackend lobby = synced("lobby");
		lobby.manifest();
		lobby.reportLoad(library.version(), 0, List.of("cannot understand this condition"));
		lobby.ping(1);

		awaitWarning("cannot understand this condition");
	}

	@Test
	void pushesToEveryReadyBackend() throws IOException {
		server.scripts(libraryWith("global/coins.sk", "on load:"));
		synced("lobby").manifest();
		synced("survival").manifest();

		assertEquals(2, server.push(true));
	}

	@Test
	void sendsNothingWhenNoScriptChanged() throws IOException {
		server.scripts(libraryWith("global/coins.sk", "on load:"));
		synced("lobby").manifest();

		assertEquals(0, server.push(false));
	}

	@Test
	void saysScriptSharingIsOffWhenNoLibraryIsSet() {
		assertFalse(server.sharingScripts());
		assertEquals("off", server.scriptSummary());
		assertEquals(0, server.push(true));
	}

	@Test
	void summarisesTheLibraryItHolds() throws IOException {
		server.scripts(libraryWith("global/coins.sk", "on load:"));

		assertTrue(server.sharingScripts());
		assertTrue(server.scriptSummary().contains("1 file(s)"));
	}

	@Test
	void dropsABackendThatSendsAFrameItCannotRead() throws IOException {
		FakeBackend lobby = synced("lobby");

		lobby.sendRaw(new Frame(Protocol.MUTATE, new byte[] {1, 2}));

		assertThrows(IOException.class, () -> {
			for (int i = 0; i < 16; i++)
				lobby.take();
		});
	}

	private NetworkServer start(File logFile, int replayCapacity) throws IOException {
		BindException lost = null;

		for (int attempt = 0; attempt < 20; attempt++) {
			int candidate = freePort();
			NetworkServer started = new NetworkServer("127.0.0.1", candidate, TOKEN, logFile, 10, 2.0,
					replayCapacity, log);
			try {
				started.start();
				port = candidate;
				return started;
			} catch (BindException taken) {
				lost = taken;
				started.stop();
			}
		}
		throw lost;
	}

	private FakeBackend connected(String name) throws IOException {
		return connect(name, TOKEN, Protocol.VERSION, 0);
	}

	private FakeBackend synced(String name) throws IOException {
		FakeBackend backend = connected(name);
		backend.sync();
		return backend;
	}

	private FakeBackend connectWithPlayerUuids(String name, boolean usePlayerUuids)
			throws IOException {
		FakeBackend backend = new FakeBackend(name, port, TOKEN, Protocol.VERSION, 0, 0, usePlayerUuids);
		backends.add(backend);
		return backend;
	}

	private FakeBackend connect(String name, String token, int protocol, long lastSeq)
			throws IOException {
		FakeBackend backend = new FakeBackend(name, port, token, protocol, lastSeq, 0);
		backends.add(backend);
		return backend;
	}

	private ScriptLibrary libraryWith(String path, String content) throws IOException {
		File dataFolder = new File(folder, "proxy-data");
		File file = new File(new File(dataFolder, "scripts"), path);
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);

		ScriptLibrary library = new ScriptLibrary(dataFolder, log, 512 * 1024L, 16 * 1024 * 1024L);
		library.rescan();
		return library;
	}

	private void awaitLog(String fragment) {
		await(() -> log.sawAny(fragment), "no log line mentioning '" + fragment + "'");
	}

	private void awaitWarning(String fragment) {
		await(() -> log.sawWarning(fragment), "no warning mentioning '" + fragment + "'");
	}

	private static void await(BooleanSupplier condition, String message) {
		long deadline = System.currentTimeMillis() + FakeBackend.TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean())
				return;
			Thread.onSpinWait();
		}
		fail(message);
	}

	private static int freePort() throws IOException {
		ServerSocket probe = new ServerSocket();
		try (probe) {
			probe.setReuseAddress(true);
			probe.bind(new InetSocketAddress("127.0.0.1", 0));
			return probe.getLocalPort();
		}
	}
}
