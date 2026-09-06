package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import sknetwork.common.Log;
import sknetwork.common.Manifest;
import sknetwork.common.ScriptEntry;
import sknetwork.common.ScriptPath;

public final class ScriptLibrary {


	private final File root;
	private final File versionFile;
	private final Log log;
	private final long maxFileBytes;
	private final long maxTotalBytes;

	private volatile long version;
	private volatile Map<String, Script> scripts = Map.of();
	private volatile ServerGroups groups = new ServerGroups(Map.of());

	/** One file: where it lives, which group folder it came from, and its bytes. */
	private record Script(String path, String group, String sha256, byte[] content) {
	}

	/**
	 * @param dataFolder the plugin folder; scripts live in {@code scripts/} beneath
	 *                   it and the version is recorded beside that
	 */
	public ScriptLibrary(File dataFolder, Log log, long maxFileBytes, long maxTotalBytes) {
		this.root = new File(dataFolder, "scripts");
		this.versionFile = new File(dataFolder, "scripts.version");
		this.log = log;
		this.maxFileBytes = maxFileBytes;
		this.maxTotalBytes = maxTotalBytes;
		this.version = readVersion();
	}


	private long readVersion() {
		if (!versionFile.isFile())
			return 0;
		try {
			return Long.parseLong(Files.readString(versionFile.toPath()).trim());
		} catch (IOException | NumberFormatException e) {
			log.warn("could not read " + versionFile.getName() + ", starting the manifest at 0");
			return 0;
		}
	}

	private void bump() {
		version++;
		try {
			File parent = versionFile.getParentFile();
			if (parent != null && !parent.isDirectory())
				parent.mkdirs();
			Files.writeString(versionFile.toPath(), Long.toString(version));
		} catch (IOException e) {
			log.warn("could not record the manifest version: " + e.getMessage());
		}
	}

	public long version() {
		return version;
	}

	public int fileCount() {
		return scripts.size();
	}

	public void groups(ServerGroups groups) {
		this.groups = groups;
	}

	public ServerGroups groups() {
		return groups;
	}

	/** @return the bytes to send for a FETCH, or null if it went away since the manifest */
	public byte[] content(String path) {
		Script script = scripts.get(path);
		return script == null ? null : script.content();
	}

	/**
	 * Rereads the folder. Only bumps the version when something actually differs,
	 * so a push with no edits costs each backend one small frame.
	 *
	 * @return true if anything changed
	 */
	public synchronized boolean rescan() {
		Map<String, Script> found = new LinkedHashMap<>();
		if (!root.isDirectory()) {
			if (scripts.isEmpty())
				return false;
			scripts = found;
			bump();
			return true;
		}

		long total = 0;
		List<String> folders = new ArrayList<>();
		Path base = root.toPath();

		try (Stream<Path> walk = Files.walk(base)) {
			List<Path> files = walk.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList();
			for (Path file : files) {
				String relative = base.relativize(file).toString().replace(File.separatorChar, '/');
				if (!ScriptPath.isSafe(relative)) {
					if (relative.endsWith(ScriptPath.EXTENSION))
						log.warn("skipping '" + relative + "': not a name we will push");
					continue;
				}

				int slash = relative.indexOf('/');
				if (slash <= 0) {
					log.warn("skipping '" + relative + "': scripts go in a group folder, "
							+ "not loose in scripts/");
					continue;
				}
				String group = relative.substring(0, slash);
				if (!folders.contains(group))
					folders.add(group);

				long size = Files.size(file);
				if (size > maxFileBytes) {
					log.warn("skipping '" + relative + "': " + size + " bytes is over the "
							+ maxFileBytes + " byte limit");
					continue;
				}
				total += size;
				if (total > maxTotalBytes) {
					log.warn("stopping the scan at '" + relative + "': the library is over the "
							+ maxTotalBytes + " byte limit");
					break;
				}

				byte[] content = Files.readAllBytes(file);
				found.put(relative, new Script(relative, group, Manifest.hash(content), content));
			}
		} catch (IOException e) {
			log.error("could not read " + root, e);
			return false;
		}

		for (String missing : groups.undefined(folders))
			log.warn("scripts/" + missing + "/ has no matching group in config.yml, "
					+ "so nothing in it will be pushed anywhere");

		if (sameAs(found))
			return false;

		scripts = found;
		bump();
		return true;
	}

	private boolean sameAs(Map<String, Script> candidate) {
		Map<String, Script> current = scripts;
		if (current.size() != candidate.size())
			return false;
		for (Map.Entry<String, Script> entry : candidate.entrySet()) {
			Script existing = current.get(entry.getKey());
			if (existing == null || !existing.sha256().equals(entry.getValue().sha256()))
				return false;
		}
		return true;
	}

	/** What this one server should be holding. */
	public Manifest manifestFor(String serverName) {
		List<ScriptEntry> entries = new ArrayList<>();
		for (Script script : scripts.values())
			if (groups.reaches(script.group(), serverName))
				entries.add(new ScriptEntry(script.path(), script.sha256()));
		return new Manifest(version, entries);
	}

	/** Creates the folder and a note, so the feature is discoverable. */
	public void ensureFolder() {
		File global = new File(root, ServerGroups.GLOBAL);
		if (global.isDirectory())
			return;
		if (!global.mkdirs()) {
			log.warn("could not create " + global);
			return;
		}
		try {
			Files.writeString(new File(root, "README.txt").toPath(),
					"""
					Scripts here are pushed to backend servers by skNetwork.

					  global/    every connected server
					  <name>/    the servers listed under 'scripts.groups.<name>' in config.yml

					Backends receive these in plugins/Skript/scripts/network/ and must not be
					edited there - the next push overwrites them. Edit here instead.

					Push with /sknet push on the proxy console.
					""", StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("could not write the scripts README: " + e.getMessage());
		}
	}
}
