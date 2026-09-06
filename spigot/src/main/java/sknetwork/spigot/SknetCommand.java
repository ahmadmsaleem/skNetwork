package sknetwork.spigot;

import java.util.Locale;

import sknetwork.common.Protocol;
import sknetwork.common.Style;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** {@code /sknet}. */
final class SknetCommand implements CommandExecutor {

	private final SkNetworkSpigot plugin;

	SknetCommand(SkNetworkSpigot plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String label, @NotNull String @NotNull [] args) {
		String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

		switch (sub) {
			case "resync" -> resync(sender);
			case "reconnect" -> reconnect(sender);
			case "" -> status(sender);
			default -> {
				sender.sendMessage(SknetStyle.header(version(), Protocol.VERSION));
				sender.sendMessage(SknetStyle.gap());
				sender.sendMessage(SknetStyle.note("no such subcommand '<name>'",
						SknetStyle.text("name", args[0])));
				sender.sendMessage(SknetStyle.gap());
				usage(sender);
				sender.sendMessage(SknetStyle.gap());
			}
		}
		return true;
	}


	/** Throws the mirror away and pulls the whole map back, rather than resuming. */
	private void resync(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		if (client == null || applier == null) {
			sender.sendMessage(SknetStyle.note("network variables are not running on this server"));
			return;
		}

		applier.requestFullSnapshot();
		client.dropConnection();
		sender.sendMessage(SknetStyle.row("Resync", "<green>asked for a full snapshot"));
		sender.sendMessage(SknetStyle.note("reads keep working from the copy until it lands"));
	}

	/** The cheap one: the proxy replays only what was missed. */
	private void reconnect(CommandSender sender) {
		ProxyClient client = plugin.client();
		if (client == null) {
			sender.sendMessage(SknetStyle.note("network variables are not running on this server"));
			return;
		}

		client.dropConnection();
		sender.sendMessage(SknetStyle.row("Reconnect", "<green>resuming from seq <white><seq>",
				SknetStyle.text("seq", Style.number(client.lastSeq()))));
		sender.sendMessage(SknetStyle.note("use <white>/sknet resync<dark_gray> to pull everything instead"));
	}

	private void status(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		AtomicRequests requests = plugin.requests();
		SyncState state = client == null ? SyncState.DISCONNECTED : client.state();

		sender.sendMessage(SknetStyle.header(version(), Protocol.VERSION));
		sender.sendMessage(SknetStyle.gap());

		sender.sendMessage(SknetStyle.row("State",
				"<" + colour(state) + "><state>"
						+ (state == SyncState.READY ? "" : "<gray>  writes are refused"),
				SknetStyle.text("state", state.toString())));

		sender.sendMessage(SknetStyle.row("Server", "<white><name>",
				SknetStyle.text("name", plugin.serverName())));

		long latency = client == null ? -1 : client.latencyMs();
		sender.sendMessage(SknetStyle.row("Proxy",
				"<white><target>" + (latency < 0 ? "" : "<dark_gray>  <ms>ms"),
				SknetStyle.text("target", client == null ? "-" : client.describeTarget()),
				SknetStyle.text("ms", Style.number(latency))));

		sender.sendMessage(SkNetworkStorage.isConfigured()
				? SknetStyle.row("Storage", "<white><prefix><dark_gray>  routing <pattern>",
						SknetStyle.text("prefix", plugin.prefix()),
						SknetStyle.text("pattern", SkNetworkStorage.pattern()))
				: SknetStyle.row("Storage", "<red>NOT CONFIGURED"));

		sender.sendMessage(SknetStyle.row("Mirror",
				"<white><count> <gray>variables<dark_gray>  seq <seq>",
				SknetStyle.text("count", Style.number(applier == null ? 0 : applier.mirroredCount())),
				SknetStyle.text("seq", Style.number(client == null ? 0 : client.lastSeq()))));

		long dropped = applier == null ? 0 : applier.dropped();
		sender.sendMessage(SknetStyle.row("Applied",
				"<white><applied> <gray>inbound"
						+ (dropped == 0 ? "" : "<dark_gray>  ·  <red><dropped> <gray>dropped"),
				SknetStyle.text("applied", Style.number(applier == null ? 0 : applier.applied())),
				SknetStyle.text("dropped", Style.number(dropped))));

		long refused = plugin.droppedWrites();
		if (refused > 0)
			sender.sendMessage(SknetStyle.row("Refused", "<red><count> <gray>outbound write(s)",
					SknetStyle.text("count", Style.number(refused))));
		if (requests != null && requests.pending() > 0)
			sender.sendMessage(SknetStyle.row("Waiting",
					"<yellow><count> <gray>atomic change(s) unanswered",
					SknetStyle.text("count", Style.number(requests.pending()))));
		if (client != null && client.lastError() != null)
			sender.sendMessage(SknetStyle.row("Last error", "<red><error>",
					SknetStyle.text("error", client.lastError())));

		sender.sendMessage(SknetStyle.gap());
		usage(sender);
		sender.sendMessage(SknetStyle.gap());
	}

	private void usage(CommandSender sender) {
		sender.sendMessage(SknetStyle.hint("/sknet resync", "throw the copy away and pull everything"));
		sender.sendMessage(SknetStyle.hint("/sknet reconnect", "drop the connection and resume"));
	}

	private static String colour(SyncState state) {
		return switch (state) {
			case READY -> "green";
			case SYNCING, CONNECTING -> "yellow";
			case DISCONNECTED -> "red";
		};
	}

	private String version() {
		return plugin.getPluginMeta().getVersion();
	}
}
