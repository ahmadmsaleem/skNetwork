package sknetwork.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything one server should be holding, and the version that says so. */
public final class Manifest {


	/** A backend that has never been pushed to reports this. */
	public static final long NO_VERSION = 0;

	private final long version;
	private final Map<String, String> hashesByPath = new LinkedHashMap<>();

	public Manifest(long version, List<ScriptEntry> entries) {
		this.version = version;
		for (ScriptEntry entry : entries)
			hashesByPath.put(entry.path(), entry.sha256());
	}

	public long version() {
		return version;
	}

	public int size() {
		return hashesByPath.size();
	}

	public Map<String, String> hashesByPath() {
		return hashesByPath;
	}

	public List<ScriptEntry> entries() {
		List<ScriptEntry> out = new ArrayList<>(hashesByPath.size());
		hashesByPath.forEach((path, hash) -> out.add(new ScriptEntry(path, hash)));
		return out;
	}

	public void write(PacketOut out) {
		out.int64(version).varInt(hashesByPath.size());
		hashesByPath.forEach((path, hash) -> out.string(path).string(hash));
	}

	public static Manifest read(PacketIn in) throws IOException {
		long version = in.int64();
		int count = in.varInt();
		List<ScriptEntry> entries = new ArrayList<>(Math.max(count, 0));
		for (int i = 0; i < count; i++)
			entries.add(new ScriptEntry(in.string(), in.string()));
		return new Manifest(version, entries);
	}

	public static String hash(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException e) {
			// every JVM ships SHA-256, so this cannot happen
			throw new IllegalStateException(e);
		}
	}

	public static String hash(String content) {
		return hash(content.getBytes(StandardCharsets.UTF_8));
	}
}
