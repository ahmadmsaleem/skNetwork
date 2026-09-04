package sknetwork.proxy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bstats.bungeecord.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import sknetwork.common.Log;
import sknetwork.common.Protocol;
import sknetwork.common.SkNetwork;
import sknetwork.common.Style;
import sknetwork.proxy.core.ConfigSource;
import sknetwork.proxy.core.NetworkServer;
import sknetwork.proxy.core.ProxyBoot;
import sknetwork.proxy.core.ProxySettings;
import sknetwork.proxy.core.SknetConsole;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

/**
 * The proxy half, where the data actually lives.
 * Everything real is in {@link NetworkServer} and {@link ProxyBoot}, neither of
 * which has a BungeeCord import, so {@link SkNetworkVelocity} is this class
 * again against a different plugin API and nothing else.
 */
public final class SkNetworkBungee extends Plugin {

	private NetworkServer server;

	@Override
	public void onEnable() {
		Configuration config;
		try {
			config = loadConfig();
		} catch (IOException e) {
			getLogger().severe("could not read config.yml, refusing to start: " + e);
			return;
		}

		ProxySettings settings = ProxySettings.from(new BungeeConfig(config));
		Log log = bungeeLog(getLogger(), settings.debug());

		try {
			server = ProxyBoot.start(settings, getDataFolder(), log);
		} catch (IOException e) {
			getLogger().severe("could not bind " + settings.bind() + ":" + settings.port()
					+ " - " + e.getMessage());
			server = null;
			return;
		}

		startMetrics();
		getProxy().getPluginManager().registerCommand(this, new SknetCommand());

		getLogger().info(SkNetwork.NAME + " " + getDescription().getVersion()
				+ " (protocol " + Protocol.VERSION + ") running as the PROXY half");
	}

	private void startMetrics() {
		Metrics metrics = new Metrics(this, SkNetwork.BSTATS_BUNGEECORD);
		metrics.addCustomChart(new SingleLineChart("backends", server::connectionCount));
		metrics.addCustomChart(new SingleLineChart("network_variables", server::variableCount));
		metrics.addCustomChart(new SimplePie("script_distribution",
				() -> server.sharingScripts() ? "on" : "off"));
	}

	@Override
	public void onDisable() {
		if (server != null)
			server.stop();
		getLogger().info(SkNetwork.NAME + " stopped");
	}

	/** {@code /sknetproxy} on the proxy console. */
	private final class SknetCommand extends Command {

		private SknetCommand() {
			// not "sknet": a proxy intercepts a command it knows, so that name has to
			// stay free for the backend half, where an operator already has permission
			super("sknetproxy", "sknetwork.admin", "sknetp", "sknp");
		}

		// BungeeCord deprecated its whole chat API in favour of Adventure, which it does
		// not ship. fromLegacyText stays the only way to colour a line here.
		@Override
		@SuppressWarnings("deprecation")
		public void execute(CommandSender sender, String[] args) {
			boolean hex = sender instanceof ProxiedPlayer;
			SknetConsole.run(server, getDescription().getVersion(), args,
					line -> sender.sendMessage(TextComponent.fromLegacyText(
							hex ? line : Style.downgrade(line))));
		}
	}

	/** BungeeCord's own {@code Configuration} already speaks dotted paths. */
	private record BungeeConfig(Configuration config) implements ConfigSource {

		@Override
		public String string(String path, String fallback) {
			return config.getString(path, fallback);
		}

		@Override
		public int integer(String path, int fallback) {
			return config.getInt(path, fallback);
		}

		@Override
		public double number(String path, double fallback) {
			return config.getDouble(path, fallback);
		}

		@Override
		public boolean flag(String path, boolean fallback) {
			return config.getBoolean(path, fallback);
		}

		@Override
		public List<String> keys(String path) {
			Configuration section = config.getSection(path);
			return section == null ? List.of() : new ArrayList<>(section.getKeys());
		}

		@Override
		public List<String> stringList(String path) {
			return new ArrayList<>(config.getStringList(path));
		}
	}

	private static Log bungeeLog(Logger logger, boolean debug) {
		return new Log() {
			@Override
			public void info(String message) {
				logger.info(message);
			}

			@Override
			public void warn(String message) {
				logger.warning(message);
			}

			@Override
			public void error(String message, Throwable error) {
				logger.log(Level.SEVERE, message, error);
			}

			@Override
			public void debug(String message) {
				if (debug)
					logger.info("[debug] " + message);
			}
		};
	}

	private Configuration loadConfig() throws IOException {
		File folder = getDataFolder();
		if (!folder.isDirectory() && !folder.mkdirs())
			throw new IOException("could not create " + folder);

		File file = new File(folder, "config.yml");
		if (!file.exists()) {
			try (InputStream in = getResourceAsStream("proxy-config.yml")) {
				if (in == null)
					throw new IOException("proxy-config.yml missing from the jar");
				Files.copy(in, file.toPath());
			}
		}
		return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
	}
}
