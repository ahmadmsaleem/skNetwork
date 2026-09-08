package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

import sknetwork.common.Log;
import sknetwork.common.PingField;
import sknetwork.common.PingSettings;

final class PingState {

	private static final String FILE = "ping.properties";

	private final File file;
	private final Log log;

	private volatile PingSettings overrides = PingSettings.NONE;

	PingState(File dataFolder, Log log) {
		this.file = new File(dataFolder, FILE);
		this.log = log;
	}

	PingSettings overrides() {
		return overrides;
	}

	PingSettings effective(PingSettings configured) {
		return overrides.over(configured);
	}

	synchronized void set(PingField field, String value) {
		overrides = overrides.with(field, value == null || value.isBlank() ? null : value);
		save();
	}

	synchronized void load() {
		if (!file.isFile())
			return;

		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file.toPath())) {
			properties.load(in);
		} catch (IOException e) {
			log.warn("could not read " + FILE + ", starting with nothing set: " + e.getMessage());
			return;
		}

		overrides = new PingSettings(
				properties.getProperty(key(PingField.MOTD)),
				properties.getProperty(key(PingField.MAX_PLAYERS)),
				properties.getProperty(key(PingField.PLAYER_COUNT)));

		if (!overrides.isEmpty())
			log.info("server list ping: restored " + describe() + " from " + FILE);
	}

	private void save() {
		if (overrides.isEmpty()) {
			try {
				Files.deleteIfExists(file.toPath());
			} catch (IOException e) {
				log.warn("could not remove " + FILE + ": " + e.getMessage());
			}
			return;
		}

		Properties properties = new Properties();
		for (PingField field : PingField.values()) {
			String value = overrides.get(field);
			if (value != null)
				properties.setProperty(key(field), value);
		}

		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory())
				parent.mkdirs();
			try (OutputStream out = Files.newOutputStream(file.toPath())) {
				properties.store(out, "skNetwork server list ping, set by scripts. "
						+ "Delete this file to fall back to config.yml.");
			}
		} catch (IOException e) {
			log.error("could not write " + FILE + " - the ping is live but will not survive "
					+ "a restart", e);
		}
	}

	String describe() {
		StringBuilder out = new StringBuilder();
		for (PingField field : PingField.values()) {
			String value = overrides.get(field);
			if (value == null)
				continue;
			if (!out.isEmpty())
				out.append(", ");
			out.append(key(field)).append('=').append(value);
		}
		return out.isEmpty() ? "nothing" : out.toString();
	}

	private static String key(PingField field) {
		return field.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}
}
