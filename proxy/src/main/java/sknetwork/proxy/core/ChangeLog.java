package sknetwork.proxy.core;

import java.io.IOException;

/** Where the proxy's writes are persisted. */
interface ChangeLog {


	/** @return the highest sequence number stored, which the proxy resumes from */
	long open(VariableStore store) throws IOException;

	/** A null value records a delete. Called on the writer thread only. */
	void append(long seq, String name, String type, byte[] value, String display);

	void flush();

	void maybeCompact(VariableStore store, long seq);

	void close();
}
