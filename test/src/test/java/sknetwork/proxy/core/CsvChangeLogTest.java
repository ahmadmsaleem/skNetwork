package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

		assertEquals(2, lines.size());
		assertEquals("# skNetwork v2 seq=1500", lines.get(0));
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

		assertEquals(501, Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).size());
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

	private long open(VariableStore store) throws IOException {
		CsvChangeLog changeLog = new CsvChangeLog(file, 2.0, log);
		long highWater = changeLog.open(store);
		changeLog.close();
		return highWater;
	}
}
