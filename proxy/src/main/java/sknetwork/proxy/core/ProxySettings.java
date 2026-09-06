package sknetwork.proxy.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sknetwork.common.Durations;
import sknetwork.common.Protocol;

public record ProxySettings(String bind, int port, String token, boolean debug,
		String logName, long flushIntervalMs, double compactRatio, int replayBuffer,
		boolean scriptsEnabled, Map<String, List<String>> groups,
		long maxFileBytes, long maxTotalBytes, boolean players, boolean remoteCommands) {

	public static ProxySettings from(ConfigSource config) {
		Map<String, List<String>> groups = new LinkedHashMap<>();
		for (String name : config.keys("scripts.groups"))
			groups.put(name, config.stringList("scripts.groups." + name));

		return new ProxySettings(
				config.string("bind", "127.0.0.1"),
				config.integer("port", Protocol.DEFAULT_PORT),
				config.string("token", "change-me"),
				config.flag("debug", false),
				config.string("log", "network.csv"),
				Durations.millis(config.string("flush-interval", null), 100),
				config.number("compact-when", 2.0),
				config.integer("replay-buffer", 10_000),
				config.flag("scripts.enabled", false),
				groups,
				config.integer("scripts.max-file-kb", 512) * 1024L,
				config.integer("scripts.max-total-mb", 16) * 1024L * 1024L,
				config.flag("players", true),
				config.flag("remote-commands", false));
	}

	/** Null, blank or "none" all mean keep nothing. */
	public boolean persists() {
		return logName != null && !logName.isBlank() && !logName.equalsIgnoreCase("none");
	}

	public boolean tokenIsExposedDefault() {
		return "change-me".equals(token) && !"127.0.0.1".equals(bind);
	}
}
