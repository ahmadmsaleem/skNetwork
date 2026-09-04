package sknetwork.spigot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import sknetwork.common.Manifest;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;
import sknetwork.common.ScriptPath;

/**
 * The backend's copy of the pushed scripts. Diffs a manifest against what is on
 * disk, asks for what is missing, and swaps the whole set in at once.
 */
final class ScriptSync {


	private static final String FOLDER = "network";
	private static final String STAGING = "network.staging";

	private final SkNetworkSpigot plugin;
	private final File root;
	private final File staging;

	/** The push we are in the middle of, or null when idle. */
	private Manifest pending;
	private Map<String, byte[]> arrived;
	private int expected;
	private long appliedVersion = Manifest.NO_VERSION;

	ScriptSync(SkNetworkSpigot plugin, File scriptsFolder) {
		this.plugin = plugin;
		this.root = new File(scriptsFolder, FOLDER);
		this.staging = new File(scriptsFolder, STAGING);
	}

	File root() {
		return root;
	}

	long appliedVersion() {
		return appliedVersion;
	}

	int fileCount() {
		return onDisk().size();
	}

	/**
	 * A fresh manifest. Works out what is missing and asks for it; applies straight
	 * away when nothing changed.
	 */
	void onManifest(Manifest manifest) {
		Map<String, String> have = onDisk();
		List<String> wanted = new ArrayList<>();

		manifest.hashesByPath().forEach((path, hash) -> {
			if (!ScriptPath.isSafe(path)) {
				plugin.getLogger().warning("the proxy sent an unsafe script path and it was "
						+ "ignored: " + path);
				return;
			}
			if (!hash.equals(have.get(path)))
				wanted.add(path);
		});

		pending = manifest;
		arrived = new LinkedHashMap<>();
		expected = wanted.size();

		if (wanted.isEmpty()) {
			apply();
			return;
		}

		PacketOut out = new PacketOut(Protocol.FETCH)
				.int64(manifest.version())
				.varInt(wanted.size());
		for (String path : wanted)
			out.string(path);
		plugin.client().send(out.frame());
	}


	/** One file's bytes. A null body means the proxy no longer has it. */
	void onFile(long version, String path, byte[] content) {
		if (pending == null || version != pending.version())
			return;

		if (content == null) {
			plugin.getLogger().info("the proxy dropped " + path + " mid push, waiting for the "
					+ "next manifest");
			abort();
			return;
		}
		if (!ScriptPath.isSafe(path) || !pending.hashesByPath().containsKey(path)) {
			plugin.getLogger().warning("ignoring an unexpected script from the proxy: " + path);
			return;
		}
		if (!Manifest.hash(content).equals(pending.hashesByPath().get(path))) {
			plugin.getLogger().warning(path + " arrived with the wrong hash, abandoning this push");
			abort();
			return;
		}

		arrived.put(path, content);
		if (arrived.size() >= expected)
			apply();
	}

	private void abort() {
		pending = null;
		arrived = null;
		expected = 0;
	}

	/** Writes everything, drops what the manifest no longer lists, reloads once. */
	private void apply() {
		Manifest manifest = pending;
		Map<String, byte[]> files = arrived;
		abort();
		if (manifest == null)
			return;

		try {
			delete(staging);
			for (Map.Entry<String, byte[]> entry : files.entrySet())
				write(new File(staging, entry.getKey()), entry.getValue());
		} catch (IOException e) {
			plugin.getLogger().severe("could not stage the pushed scripts: " + e.getMessage());
			delete(staging);
			return;
		}

		SkriptScripts.unloadUnder(root);

		String failure = null;
		try {
			// Deletions first. A path that used to be a file and is now a folder cannot
			// be created while the old file still sits there.
			removeUnlisted(manifest);
			for (String path : manifest.hashesByPath().keySet()) {
				File staged = new File(staging, path);
				if (staged.isFile())
					move(staged, new File(root, path));
			}
		} catch (IOException e) {
			failure = e.getMessage();
		} finally {
			delete(staging);
		}

		writeNotice();
		SkriptScripts.LoadReport report = SkriptScripts.reload(root);

		List<SkriptScripts.LoadProblem> problems = new ArrayList<>(report.problems());
		if (failure == null) {
			appliedVersion = manifest.version();
		} else {
			// leaving the version alone is what makes the next push retry. recording it
			// would strand this server on the old scripts with the proxy reporting success
			problems.add(new SkriptScripts.LoadProblem("", 0,
					"could not apply manifest " + manifest.version() + ": " + failure, true));
			plugin.getLogger().severe("could not apply the pushed scripts: " + failure);
		}

		SkriptScripts.LoadReport sent = new SkriptScripts.LoadReport(report.loaded(), problems);
		int errorCount = sent.errors().size();
		int warningCount = sent.warnings().size();

		if (errorCount > 0)
			plugin.getLogger().warning("loaded " + report.loaded() + " pushed script(s) from manifest "
					+ manifest.version() + " with " + errorCount + " error(s)");
		else
			plugin.getLogger().info("loaded " + report.loaded() + " pushed script(s) from manifest "
					+ manifest.version() + (warningCount == 0 ? "" : ", " + warningCount + " warning(s)"));

		send(manifest.version(), sent);
	}

	private void send(long version, SkriptScripts.LoadReport report) {
		PacketOut out = new PacketOut(Protocol.LOAD_RESULT)
				.int64(version)
				.varInt(report.loaded())
				.varInt(report.problems().size());
		for (SkriptScripts.LoadProblem problem : report.problems())
			out.string(problem.path()).varInt(problem.line()).string(problem.message())
					.bool(problem.severe());
		plugin.client().send(out.frame());
	}

	/** Anything under network/ the manifest does not mention is gone from the proxy. */
	private void removeUnlisted(Manifest manifest) {
		for (String path : onDisk().keySet()) {
			if (manifest.hashesByPath().containsKey(path))
				continue;
			File file = new File(root, path);
			if (file.delete())
				plugin.getLogger().info("removed " + path + ", the proxy no longer has it");
		}
		pruneEmptyFolders(root);
	}

	/** @return path -> hash for everything currently under network/ */
	private Map<String, String> onDisk() {
		Map<String, String> found = new LinkedHashMap<>();
		if (!root.isDirectory())
			return found;

		Path base = root.toPath();
		try (Stream<Path> walk = Files.walk(base)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				String relative = base.relativize(file).toString().replace(File.separatorChar, '/');
				if (relative.endsWith(ScriptPath.EXTENSION))
					found.put(relative, Manifest.hash(Files.readAllBytes(file)));
			}
		} catch (IOException e) {
			plugin.getLogger().warning("could not read " + root + ": " + e.getMessage());
		}
		return found;
	}

	private void write(File target, byte[] content) throws IOException {
		File parent = target.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			throw new IOException("could not create " + parent);
		Files.write(target.toPath(), content);
	}

	private void move(File from, File to) throws IOException {
		File parent = to.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			throw new IOException("could not create " + parent);
		Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private void pruneEmptyFolders(File folder) {
		File[] children = folder.listFiles();
		if (children == null)
			return;
		for (File child : children)
			if (child.isDirectory()) {
				pruneEmptyFolders(child);
				String[] left = child.list();
				if (left != null && left.length == 0)
					child.delete();
			}
	}

	private void delete(File folder) {
		if (!folder.exists())
			return;
		try (Stream<Path> walk = Files.walk(folder.toPath())) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException ignored) {
		}
	}

	private void writeNotice() {
		if (!root.isDirectory())
			return;
		try {
			Files.writeString(new File(root, "README.txt").toPath(),
					"""
					Managed by skNetwork. Everything here is overwritten on the next push.

					Edit the originals on the proxy, in plugins/skNetwork/scripts/, then run
					/sknet push on the proxy console.
					""", StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}
}
