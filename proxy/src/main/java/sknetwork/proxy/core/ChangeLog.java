package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;

interface ChangeLog {


	long open(VariableStore store) throws IOException;

	boolean mayBeMissingKeys();

	void append(long seq, String name, String type, byte[] value, String display);

	void noPersist(NamePatterns patterns, VariableStore store, long seq);

	void flush();

	void maybeCompact(VariableStore store, long seq);

	void compact(VariableStore store, long seq);

	long bytes();

	long dataLines();

	long lastCompaction();

	long lastFlush();

	long compactThreshold(long liveKeys);

	File backup() throws IOException;

	void close();
}
