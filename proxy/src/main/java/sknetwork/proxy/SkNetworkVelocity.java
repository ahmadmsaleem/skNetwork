package sknetwork.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import sknetwork.common.Log;
import sknetwork.common.Protocol;
import sknetwork.common.SkNetwork;
import sknetwork.proxy.core.ConfigSource;
import sknetwork.proxy.core.NetworkServer;
import sknetwork.proxy.core.ProxyBoot;
import sknetwork.proxy.core.ProxySettings;
import sknetwork.proxy.core.SknetConsole;

/**
 * The proxy half on Velocity. Same config file, same data, same protocol as
 * {@link SkNetworkBungee} - the shared work is in {@code ProxyBoot} and
 * {@code SknetConsole}, and this class only reads a config and registers a
 * command.
 */
public final class SkNetworkVelocity {

	/** Velocity speaks Adventure, so the shared legacy strings are converted here. */
	private static final LegacyComponentSerializer LEGACY =
			LegacyComponentSerializer.builder().character('\u00a7').hexColors().build();


	private final ProxyServer proxy;
	private final Logger logger;
	private final Path dataDirectory;
	private final Metrics.Factory metricsFactory;

	private NetworkServer server;

	@Inject
	public SkNetworkVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory,
			Metrics.Factory metricsFactory) {
		this.proxy = proxy;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
		this.metricsFactory = metricsFactory;
	}

	@Subscribe
	public void onProxyInitialize(ProxyInitializeEvent event) {
		ConfigurationNode config;
		try {
			config = loadConfig();
		} catch (IOException e) {
			logger.error("could not read config.yml, refusing to start: {}", e.toString());
			return;
		}

		ProxySettings settings = ProxySettings.from(new NodeConfig(config));
		Log log = velocityLog(logger, settings.debug());

		try {
			server = ProxyBoot.start(settings, dataDirectory.toFile(), log);
		} catch (IOException e) {
			logger.error("could not bind {}:{} - {}", settings.bind(), settings.port(), e.getMessage());
			server = null;
			return;
		}

		startMetrics();

		CommandManager commands = proxy.getCommandManager();
		commands.register(commands.metaBuilder("sknetproxy")
				.aliases("sknetp", "sknp").plugin(this).build(), new SknetCommand());

		logger.info("{} {} (protocol {}) running as the PROXY half",
				SkNetwork.NAME, version(), Protocol.VERSION);
	}

	/** Same charts as the BungeeCord half, so the two pages read the same way. */
	private void startMetrics() {
		Metrics metrics = metricsFactory.make(this, SkNetwork.BSTATS_VELOCITY);
		metrics.addCustomChart(new SingleLineChart("backends", server::connectionCount));
		metrics.addCustomChart(new SingleLineChart("network_variables", server::variableCount));
		metrics.addCustomChart(new SimplePie("script_distribution",
				() -> server.sharingScripts() ? "on" : "off"));
	}

	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		if (server != null)
			server.stop();
		logger.info("{} stopped", SkNetwork.NAME);
	}

	private String version() {
		return proxy.getPluginManager().fromInstance(this)
				.flatMap(container -> container.getDescription().getVersion())
				.orElse("unknown");
	}

	/** {@code /sknet} on the proxy console. */
	private final class SknetCommand implements SimpleCommand {

		@Override
		public void execute(Invocation invocation) {
			SknetConsole.run(server, version(), invocation.arguments(),
					line -> invocation.source().sendMessage(LEGACY.deserialize(line)));
		}

		@Override
		public boolean hasPermission(Invocation invocation) {
			return invocation.source().hasPermission("sknetwork.admin");
		}
	}

	/** Configurate walks a path segment at a time rather than by a dotted string. */
	private record NodeConfig(ConfigurationNode root) implements ConfigSource {

		private ConfigurationNode at(String path) {
			return root.node((Object[]) path.split("\\."));
		}

		@Override
		public String string(String path, String fallback) {
			// not getString(fallback): Configurate rejects a null default, and
			// 'flush-interval' has one so Durations can fall back to its own
			String value = at(path).getString();
			return value == null ? fallback : value;
		}

		@Override
		public int integer(String path, int fallback) {
			return at(path).getInt(fallback);
		}

		@Override
		public double number(String path, double fallback) {
			return at(path).getDouble(fallback);
		}

		@Override
		public boolean flag(String path, boolean fallback) {
			return at(path).getBoolean(fallback);
		}

		@Override
		public List<String> keys(String path) {
			List<String> names = new ArrayList<>();
			for (Object key : at(path).childrenMap().keySet())
				names.add(String.valueOf(key));
			return names;
		}

		@Override
		public List<String> stringList(String path) {
			try {
				return new ArrayList<>(at(path).getList(String.class, List.of()));
			} catch (SerializationException e) {
				// a group written as a map rather than a list. the startup warning about
				// an undefined group is what the admin will see next
				return List.of();
			}
		}
	}

	private ConfigurationNode loadConfig() throws IOException {
		Files.createDirectories(dataDirectory);

		Path file = dataDirectory.resolve("config.yml");
		if (!Files.exists(file)) {
			try (InputStream in = getClass().getResourceAsStream("/proxy-config.yml")) {
				if (in == null)
					throw new IOException("proxy-config.yml missing from the jar");
				Files.copy(in, file);
			}
		}
		return YamlConfigurationLoader.builder().path(file).build().load();
	}

	private static Log velocityLog(Logger logger, boolean debug) {
		return new Log() {
			@Override
			public void info(String message) {
				logger.info(message);
			}

			@Override
			public void warn(String message) {
				logger.warn(message);
			}

			@Override
			public void error(String message, Throwable error) {
				logger.error(message, error);
			}

			@Override
			public void debug(String message) {
				if (debug)
					logger.info("[debug] {}", message);
			}
		};
	}
}
