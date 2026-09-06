package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvChangeLogTest {

	@TempDir
	File folder;

	private File file;
	private RecordingLog log;

	@BeforeEach
	void setUp() {
		file = new File(folder, "network.csv");
		log = new RecordingLog();
	}

	@Test
	void startsEmptyWhenThereIsNoFile() throws IOException {
		VariableStore store = new VariableStore();

		assertEquals(0, open(store));
		assertEquals(0, store.size());
		assertTrue(log.sawAny("starting empty"));
	}

	@Test
	void bringsBackWhatItWrote() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins::eult", "long", Numbers.writeLong(100), "100");
		changeLog.append(2, "name", "string", new byte[] {1, 2, 3}, "eult");
		changeLog.close();

		VariableStore store = new VariableStore();
		assertEquals(2, open(store));
		assertEquals(2, store.size());
		assertEquals(100, Numbers.readLong("long", store.get("coins::eult").value));
		assertEquals("eult", store.get("name").display);
		assertEquals(1, store.get("coins::eult").seq);
	}

	@Test
	void replaysATombstoneAsADelete() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins", "long", Numbers.writeLong(100), "100");
		changeLog.append(2, "coins", null, null, null);
		changeLog.close();

		VariableStore store = new VariableStore();

		assertEquals(2, open(store));
		assertEquals(0, store.size());
	}

	@Test
	void replaysATreeDelete() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins::a", "long", Numbers.writeLong(1), "1");
		changeLog.append(2, "coins::b", "long", Numbers.writeLong(2), "2");
		changeLog.append(3, "other", "long", Numbers.writeLong(3), "3");
		changeLog.append(4, "coins::*", null, null, null);
		changeLog.close();

		VariableStore store = new VariableStore();
		open(store);

		assertEquals(1, store.size());
		assertNotNull(store.get("other"));
	}

	@Test
	void keepsTheLastValueForARepeatedKey() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		for (int i = 1; i <= 5; i++)
			changeLog.append(i, "coins", "long", Numbers.writeLong(i), String.valueOf(i));
		changeLog.close();

		VariableStore store = new VariableStore();

		assertEquals(5, open(store));
		assertEquals(5, Numbers.readLong("long", store.get("coins").value));
	}

	@Test
	void keepsANameThatHoldsANewline() throws IOException {
		String awkward = "a name\nwith, \"everything\"";
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, awkward, "long", Numbers.writeLong(7), "7");
		changeLog.close();

		VariableStore store = new VariableStore();
		open(store);

		assertNotNull(store.get(awkward));
	}

	@Test
	void skipsTheLinesItCannotRead() throws IOException {
		Files.writeString(file.toPath(), """
				# skNetwork v2 seq=3
				1, good, long, 0000000000000001, 1
				this is not a line
				2, bad, long, zzzz, 1
				notanumber, bad, long, 01, 1
				3, alsogood, long, 0000000000000002, 2
				""", StandardCharsets.UTF_8);

		VariableStore store = new VariableStore();

		assertEquals(3, open(store));
		assertEquals(2, store.size());
		assertTrue(log.sawWarning("unreadable line"));
	}

	@Test
	void picksUpTheSequenceFromTheHeader() throws IOException {
		Files.writeString(file.toPath(), "# skNetwork v2 seq=5000\n", StandardCharsets.UTF_8);

		assertEquals(5000, open(new VariableStore()));
	}

	@Test
	void startsTheFileWithADoNotEditWarning() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins::eult", "long", Numbers.writeLong(100), "100");
		changeLog.close();

		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

		assertTrue(lines.get(0).startsWith("#"));
		assertTrue(comments().stream().anyMatch(line -> line.contains("do not modify this file")));
	}

	/** Compaction rewrites the file from nothing, so the notice has to survive it. */
	@Test
	void keepsTheWarningAfterCompacting() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);

		for (int seq = 1; seq <= 1500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 1500);
		changeLog.close();

		assertTrue(comments().stream().anyMatch(line -> line.contains("do not modify this file")));
	}

	/** The notice is only comments, so replaying a file that carries it must be unchanged. */
	@Test
	void replaysStraightPastTheWarning() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(7, "coins::eult", "long", Numbers.writeLong(100), "100");
		changeLog.close();

		VariableStore store = new VariableStore();

		assertEquals(7, open(store));
		assertEquals(1, store.size());
		assertEquals(100, Numbers.readLong("long", store.get("coins::eult").value));
	}

	@Test
	void compactsToOneLinePerLiveKey() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);

		for (int seq = 1; seq <= 1500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 1500);
		changeLog.close();

		assertEquals(1, dataLines().size());
		assertTrue(comments().contains("# skNetwork v2 seq=1500"));
		assertTrue(log.sawAny("compacted"));
		assertTrue(new File(folder, "network.csv.bak").isFile());
	}

	@Test
	void leavesASmallLogAlone() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);

		for (int seq = 1; seq <= 500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 500);
		changeLog.close();

		assertEquals(500, dataLines().size());
	}

	@Test
	void remembersTheSequenceAfterCompactingAnEmptyStore() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);

		for (int seq = 1; seq <= 1500; seq++)
			changeLog.append(seq, "gone::" + seq, null, null, null);
		changeLog.maybeCompact(store, 1500);
		changeLog.close();

		VariableStore replayed = new VariableStore();

		assertEquals(1500, open(replayed));
		assertEquals(0, replayed.size());
	}

	@Test
	void keepsWritingAfterACompaction() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);

		for (int seq = 1; seq <= 1500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 1500);
		changeLog.append(1501, "after", "long", Numbers.writeLong(9), "9");
		changeLog.close();

		VariableStore replayed = new VariableStore();

		assertEquals(1501, open(replayed));
		assertNotNull(replayed.get("after"));
		assertNotNull(replayed.get("coins"));
	}

	@Test
	void fallsBackToTheBackupWhenTheLogIsGone() throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins", "long", Numbers.writeLong(100), "100");
		changeLog.close();

		Files.move(file.toPath(), new File(folder, "network.csv.bak").toPath());

		VariableStore store = new VariableStore();

		assertEquals(1, open(store));
		assertNotNull(store.get("coins"));
		assertTrue(log.sawWarning("recovering from the backup"));
		assertTrue(file.isFile());
	}

	@Test
	void keepsNothingWhenThereIsNoLog() throws IOException {
		NoopChangeLog changeLog = new NoopChangeLog();
		VariableStore store = new VariableStore();

		assertEquals(0, changeLog.open(store));
		changeLog.append(1, "coins", "long", Numbers.writeLong(1), "1");
		changeLog.flush();
		changeLog.maybeCompact(store, 1);
		changeLog.close();

		assertEquals(0, store.size());
		assertNull(store.get("coins"));
	}

	@Test
	void neverWritesAVariableThatIsNoPersist() throws IOException {
		CsvChangeLog changeLog = noPersistLog("session::*");
		changeLog.open(new VariableStore());
		changeLog.append(1, "coins", "long", Numbers.writeLong(100), "100");
		changeLog.append(2, "session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8), "hunter2");
		changeLog.close();

		assertEquals(1, dataLines().size());
		assertFalse(fileText().contains("hunter2"));
		assertFalse(fileText().contains("session::token"));

		VariableStore replayed = new VariableStore();
		open(replayed);
		assertNotNull(replayed.get("coins"));
		assertNull(replayed.get("session::token"));
	}

	@Test
	void writesNoTombstoneForANoPersistDelete() throws IOException {
		CsvChangeLog changeLog = noPersistLog("session::*");
		changeLog.open(new VariableStore());
		changeLog.append(1, "session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8), "hunter2");
		changeLog.append(2, "session::token", null, null, null);
		changeLog.close();

		assertEquals(List.of(), dataLines());
	}

	@Test
	void leavesNoPersistOutOfACompaction() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = noPersistLog("session::*");
		changeLog.open(store);

		// live in the store, and so offered to compaction, but still not for the disk
		store.set("session::token", "string", "hunter2".getBytes(StandardCharsets.UTF_8), "hunter2", 1);
		for (int seq = 2; seq <= 1500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 1500);
		changeLog.close();

		assertEquals(1, dataLines().size());
		assertFalse(fileText().contains("hunter2"));
		assertTrue(log.sawAny("compacted"));
	}

	@Test
	void scrubsTheLogWhenThePatternIsAddedAfterTheValueWasWritten() throws IOException {
		CsvChangeLog before = new CsvChangeLog(file, 2.0, log);
		before.open(new VariableStore());
		before.append(1, "coins", "long", Numbers.writeLong(100), "100");
		before.append(2, "apikey", "string", "hunter2".getBytes(StandardCharsets.UTF_8), "hunter2");
		before.close();
		assertTrue(fileText().contains("hunter2"));

		VariableStore store = new VariableStore();
		CsvChangeLog after = noPersistLog("apikey");
		long highWater = after.open(store);
		after.close();

		assertEquals(2, highWater, "a skipped line still owns its sequence number");
		assertNull(store.get("apikey"));
		assertNotNull(store.get("coins"));
		assertFalse(fileText().contains("hunter2"), "still readable in network.csv");
		assertFalse(backupText().contains("hunter2"), "still readable in network.csv.bak");
		assertTrue(log.sawAny("no-persist"));
	}

	@Test
	void keepsTheOrdinaryBackupWhenNothingWasScrubbed() throws IOException {
		VariableStore store = new VariableStore();
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		changeLog.open(store);
		for (int seq = 1; seq <= 1500; seq++) {
			store.set("coins", "long", Numbers.writeLong(seq), String.valueOf(seq), seq);
			changeLog.append(seq, "coins", "long", Numbers.writeLong(seq), String.valueOf(seq));
		}
		changeLog.maybeCompact(store, 1500);
		changeLog.close();

		// the pre-compaction history is what .bak is for when no scrub is involved
		assertTrue(backupText().lines().filter(line -> !line.startsWith("#")).count() > 1000);
	}

	private CsvChangeLog noPersistLog(String... globs) {
		return new CsvChangeLog(file, 2.0, NamePatterns.of(List.of(globs)), log);
	}

	private String fileText() throws IOException {
		return Files.readString(file.toPath(), StandardCharsets.UTF_8);
	}

	private String backupText() throws IOException {
		File bak = new File(folder, "network.csv.bak");
		return bak.isFile() ? Files.readString(bak.toPath(), StandardCharsets.UTF_8) : "";
	}

	/** Everything that is not a comment: the lines the replay actually reads. */
	private List<String> dataLines() throws IOException {
		return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).stream()
				.filter(line -> !line.isBlank() && !line.startsWith("#"))
				.toList();
	}

	private List<String> comments() throws IOException {
		return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).stream()
				.filter(line -> line.startsWith("#"))
				.toList();
	}

	private long open(VariableStore store) throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		long highWater = changeLog.open(store);
		changeLog.close();
		return highWater;
	}
}
