package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;

import sknetwork.common.Log;

interface Storage {

	static Storage of(File file, double compactRatio, NamePatterns noPersist, Log log) {
		return file == null ? new NoopChangeLog() : new CsvChangeLog(file, compactRatio, noPersist, log);
	}

	long open(VariableStore store) throws IOException;

	boolean mayBeMissingKeys();

	void append(long seq, String name, String type, byte[] value, String display);

	void noPersist(NamePatterns patterns, VariableStore store, long seq);

	void flush();

	void close();
}
