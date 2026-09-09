package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;

import sknetwork.common.Log;

public final class ProxyBoot {
	public static NetworkServer start(ProxySettings settings, File dataFolder, Log log)
			throws IOException {
		if (settings.tokenIsExposedDefault())
			log.warn("bound to " + settings.bind() + " with the default token. Anyone who can reach "
					+ "port " + settings.port() + " can read and write every network variable you have.");

		File logFile = settings.persists() ? new File(dataFolder, settings.logName()) : null;

		NamePatterns noPersist = NamePatterns.of(settings.noPersist());
		if (!noPersist.isEmpty())
			log.info(noPersist.size() + " 'no-persist' pattern(s) set: matching variables are shared "
					+ "with every backend but never written to " + settings.logName()
					+ ", so a proxy restart starts them empty. Each backend is sent a full snapshot "
					+ "on its first sync after a restart, so none of them keeps one either.");

		NetworkServer server = new NetworkServer(settings.bind(), settings.port(), settings.token(),
				logFile, settings.flushIntervalMs(), settings.compactRatio(), noPersist,
				settings.replayBuffer(), log);
		// before start(), so the listening line and everything after it honour 'debug'
		server.debugEnabled(settings.debug());
		server.settings(settings);

		PingState ping = new PingState(dataFolder, log);
		ping.load();
		server.ping(ping);
		server.start();
		server.features(settings.players(), settings.remoteCommands());
		server.usePlayerUuids(settings.usePlayerUuids());
		configureScripts(server, settings, dataFolder, log);

		if (settings.remoteCommands())
			log.warn("'remote-commands' is on. Any script on any backend can run a console "
					+ "command on every other backend.");
		return server;
	}

	private static void configureScripts(NetworkServer server, ProxySettings settings,
			File dataFolder, Log log) {
		ScriptLibrary library = new ScriptLibrary(dataFolder, log,
				settings.maxFileBytes(), settings.maxTotalBytes());
		library.ensureFolder();

		if (!settings.scriptsEnabled()) {
			log.info("script distribution is off - turn it on with 'scripts.enabled' in config.yml");
			return;
		}

		ServerGroups groups = new ServerGroups(settings.groups());
		for (String problem : groups.problems())
			log.warn(problem);

		library.groups(groups);
		library.rescan();
		server.scripts(library);

		log.info("script distribution is on: " + library.fileCount() + " script(s) in "
				+ groups.names().size() + " group(s) plus global");
	}

	private ProxyBoot() {
	}
}
