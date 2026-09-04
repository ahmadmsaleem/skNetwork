package sknetwork.proxy.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import sknetwork.common.Log;
import sknetwork.common.VariableEntry;

/**
 * The append-only change log. Same CSV shape as Skript's variables.csv with a
 * sequence number in front:
 *
 * # skNetwork v2 seq=1044
 * 1041, coins::a1b2c3, long, 0000000000000064, 100
 * 1042, party::5::leader, string, 8004416873, eult
 * 1043, x::eult, , ,
 *
 * The last line is a delete tombstone: empty type, empty value. The fifth field
 * is the display string /sknet dump prints; a v1 log has four fields and none.
 */
final class CsvChangeLog implements ChangeLog {


	private static final String HEADER_PREFIX = "# skNetwork v";
	private static final String HEADER = "# skNetwork v2 seq=";
	private static final int MIN_LINES_BEFORE_COMPACT = 1_000;

	private final File file;
	private final File backup;
	private final File temp;
	private final double compactRatio;
	private final Log log;

	private Writer out;
	private long dataLines;
	private boolean dirty;

	CsvChangeLog(File file, double compactRatio, Log log) {
		this.file = file;
		this.backup = new File(file.getPath() + ".bak");
		this.temp = new File(file.getPath() + ".tmp");
		this.compactRatio = compactRatio;
		this.log = log;
	}

	/**
	 * Replays the log into the store and opens it for appending.
	 *
	 * @return the highest sequence number seen, which the proxy resumes from
	 */
	@Override
	public synchronized long open(VariableStore store) throws IOException {
		File source = file;
		if (!file.isFile() && backup.isFile()) {
			log.warn(file.getName() + " is missing but " + backup.getName() + " is not - "
					+ "recovering from the backup, which may be one compaction behind");
			source = backup;
		}

		long highWater = 0;
		dataLines = 0;

		if (source.isFile()) {
			List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
			int broken = 0;

			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.startsWith(HEADER_PREFIX)) {
					highWater = Math.max(highWater, headerSeq(trimmed));
					continue;
				}
				if (trimmed.isEmpty() || trimmed.startsWith("#"))
					continue;

				// four fields is a v1 line, written before values carried a display string
				String[] fields = CsvLine.split(trimmed);
				if (fields == null || fields.length < 4 || fields.length > 5) {
					broken++;
					continue;
				}

				long seq;
				try {
					seq = Long.parseLong(fields[0]);
				} catch (NumberFormatException e) {
					broken++;
					continue;
				}

				String name = fields[1];
				String type = fields[2].isEmpty() ? null : fields[2];
				String value = fields[3];
				String display = fields.length == 5 && !fields[4].isEmpty() ? fields[4] : null;

				if (type == null || value.isEmpty()) {
					store.delete(name);
				} else {
					try {
						store.set(name, type, HexFormat.of().parseHex(value), display, seq);
					} catch (IllegalArgumentException e) {
						broken++;
						continue;
					}
				}

				highWater = Math.max(highWater, seq);
				dataLines++;
			}

			if (broken > 0)
				log.warn("skipped " + broken + " unreadable line(s) in " + source.getName());
			log.info("replayed " + dataLines + " line(s) from " + source.getName() + ", "
					+ store.size() + " variable(s) live at seq " + highWater);
		} else {
			log.info("no " + file.getName() + " yet, starting empty");
		}

		out = openAppend(file, source == backup);
		return highWater;
	}


	private long headerSeq(String line) {
		int marker = line.indexOf("seq=");
		if (marker < 0)
			return 0;
		try {
			return Long.parseLong(line.substring(marker + 4).trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private Writer openAppend(File target, boolean rewriteFromBackup) throws IOException {
		if (rewriteFromBackup && backup.isFile())
			Files.copy(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

		boolean fresh = !target.isFile() || target.length() == 0;
		Writer writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(target, true), StandardCharsets.UTF_8));
		if (fresh)
			writer.write(HEADER + "0\n");
		return writer;
	}

	/** A null value writes a tombstone. Writer thread only. */
	@Override
	public synchronized void append(long seq, String name, String type, byte[] value, String display) {
		if (out == null)
			return;

		try {
			out.write(CsvLine.format(seq, name, type, value, display) + "\n");
			dataLines++;
			dirty = true;
		} catch (IOException e) {
			log.error("could not append to " + file.getName() + " - the change is live in memory "
					+ "but will not survive a restart", e);
		}
	}

	@Override
	public synchronized void flush() {
		if (out == null || !dirty)
			return;
		try {
			out.flush();
			dirty = false;
		} catch (IOException e) {
			log.error("could not flush " + file.getName(), e);
		}
	}

	@Override
	public synchronized void maybeCompact(VariableStore store, long seq) {
		if (dataLines > MIN_LINES_BEFORE_COMPACT
				&& dataLines > compactRatio * Math.max(store.size(), 1))
			compact(store, seq);
	}

	/**
	 * Rewrites the log as one line per live key. Temp file, fsync, atomic rename,
	 * keeping one backup, so a crash leaves either the old file or the new one.
	 */
	private synchronized void compact(VariableStore store, long seq) {
		List<Map.Entry<String, VariableEntry>> live = store.entries();

		try {
			flush();

			try (FileOutputStream raw = new FileOutputStream(temp);
					Writer writer = new BufferedWriter(new OutputStreamWriter(raw, StandardCharsets.UTF_8))) {
				writer.write(HEADER + seq + "\n");
				for (Map.Entry<String, VariableEntry> entry : live) {
					VariableEntry variable = entry.getValue();
					writer.write(CsvLine.format(variable.seq, entry.getKey(), variable.type,
							variable.value, variable.display) + "\n");
				}
				writer.flush();
				raw.getFD().sync();
			}

			if (out != null) {
				out.close();
				out = null;
			}

			if (file.isFile())
				Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Files.move(temp.toPath(), file.toPath(),
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

			long before = dataLines;
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(file, true), StandardCharsets.UTF_8));
			dataLines = live.size();
			dirty = false;

			log.info("compacted " + file.getName() + ": " + before + " lines -> " + dataLines);
		} catch (IOException e) {
			log.error("compaction of " + file.getName() + " failed, carrying on with the existing log", e);
			if (out == null) {
				try {
					out = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream(file, true), StandardCharsets.UTF_8));
				} catch (IOException reopen) {
					log.error("could not reopen " + file.getName() + " either - changes are no longer "
							+ "being persisted", reopen);
				}
			}
		}
	}

	@Override
	public synchronized void close() {
		flush();
		if (out == null)
			return;
		try {
			out.close();
		} catch (IOException e) {
			log.error("could not close " + file.getName(), e);
		}
		out = null;
	}


}
