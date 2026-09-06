package sknetwork.proxy.core;

import java.io.File;
import java.io.IOException;

import sknetwork.common.Log;

public final class ProxyBoot {
	/**
	 * @return the started server
	 * @throws IOException if the socket cannot be bound, in which case nothing is running
	 */
	public static NetworkServer start(ProxySettings settings, File dataFolder, Log log)
			throws IOException {
		if (settings.tokenIsExposedDefault())
			log.warn("bound to " + settings.bind() + " with the default token. Anyone who can reach "
					+ "port " + settings.port() + " can read and write every network variable you have.");

		File logFile = settings.persists() ? new File(dataFolder, settings.logName()) : null;

		NetworkServer server = new NetworkServer(settings.bind(), settings.port(), settings.token(),
				logFile, settings.flushIntervalMs(), settings.compactRatio(),
				settings.replayBuffer(), log);
		server.start();
		server.features(settings.players(), settings.remoteCommands());
		configureScripts(server, settings, dataFolder, log);

		if (settings.remoteCommands())
			log.warn("'remote-commands' is on. Any script on any backend can run a console "
					+ "command on every other backend.");
		return server;
	}

	private static void configureScripts(NetworkServer server, ProxySettings settings,
			File dataFolder, Log log) {
		if (!settings.scriptsEnabled()) {
			log.info("script distribution is off - turn it on with 'scripts.enabled' in config.yml");
			return;
		}

		ServerGroups groups = new ServerGroups(settings.groups());
		for (String problem : groups.problems())
			log.warn(problem);

		ScriptLibrary library = new ScriptLibrary(dataFolder, log,
				settings.maxFileBytes(), settings.maxTotalBytes());
		library.groups(groups);
		library.ensureFolder();
		library.rescan();
		server.scripts(library);

		log.info("script distribution is on: " + library.fileCount() + " script(s) in "
				+ groups.names().size() + " group(s) plus global");
	}

	private ProxyBoot() {
	}
}
