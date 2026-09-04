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
			@NotNull String label, String @NotNull [] args) {
		String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

		switch (sub) {
			case "resync" -> resync(sender);
			case "reconnect" -> reconnect(sender);
			case "" -> status(sender);
			default -> {
				sender.sendMessage(Style.header(version(), Protocol.VERSION));
				sender.sendMessage(Style.gap());
				sender.sendMessage(Style.note("no such subcommand '" + args[0] + "'"));
				sender.sendMessage(Style.gap());
				usage(sender);
				sender.sendMessage(Style.gap());
			}
		}
		return true;
	}


	/** Throws the mirror away and pulls the whole map back, rather than resuming. */
	private void resync(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		if (client == null || applier == null) {
			sender.sendMessage(Style.note("network variables are not running on this server"));
			return;
		}

		applier.requestFullSnapshot();
		client.dropConnection();
		sender.sendMessage(Style.rowRaw("Resync", Style.GOOD + "asked for a full snapshot"));
		sender.sendMessage(Style.note("reads keep working from the copy until it lands"));
	}

	/** The cheap one: the proxy replays only what was missed. */
	private void reconnect(CommandSender sender) {
		ProxyClient client = plugin.client();
		if (client == null) {
			sender.sendMessage(Style.note("network variables are not running on this server"));
			return;
		}

		client.dropConnection();
		sender.sendMessage(Style.rowRaw("Reconnect", Style.GOOD + "resuming from seq "
				+ Style.VALUE + Style.number(client.lastSeq())));
		sender.sendMessage(Style.note("use /sknet resync to pull everything instead"));
	}

	private void status(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		AtomicRequests requests = plugin.requests();
		SyncState state = client == null ? SyncState.DISCONNECTED : client.state();

		sender.sendMessage(Style.header(version(), Protocol.VERSION));
		sender.sendMessage(Style.gap());

		sender.sendMessage(Style.rowRaw("State", colour(state) + state
				+ (state == SyncState.READY ? "" : Style.LABEL + "  writes are refused")));
		sender.sendMessage(Style.row("Server", plugin.serverName()));

		String latency = client == null || client.latencyMs() < 0
				? ""
				: Style.dim("  " + client.latencyMs() + "ms");
		sender.sendMessage(Style.row("Proxy",
				(client == null ? "-" : client.describeTarget()) + latency));

		sender.sendMessage(Style.rowRaw("Storage", SkNetworkStorage.isConfigured()
				? Style.VALUE + plugin.prefix() + Style.dim("  routing " + SkNetworkStorage.pattern())
				: Style.BAD + "NOT CONFIGURED"));

		sender.sendMessage(Style.row("Mirror",
				Style.number(applier == null ? 0 : applier.mirroredCount())
						+ Style.LABEL + " variables"
						+ Style.dim("  seq " + Style.number(client == null ? 0 : client.lastSeq()))));

		long dropped = applier == null ? 0 : applier.dropped();
		sender.sendMessage(Style.rowRaw("Applied", Style.VALUE
				+ Style.number(applier == null ? 0 : applier.applied()) + Style.LABEL + " inbound"
				+ (dropped == 0 ? "" : Style.MUTED + "  ·  " + Style.BAD + Style.number(dropped)
						+ Style.LABEL + " dropped")));

		long refused = plugin.droppedWrites();
		if (refused > 0)
			sender.sendMessage(Style.rowRaw("Refused", Style.BAD + Style.number(refused)
					+ Style.LABEL + " outbound write(s)"));
		if (requests != null && requests.pending() > 0)
			sender.sendMessage(Style.rowRaw("Waiting", Style.WARN + requests.pending()
					+ Style.LABEL + " atomic change(s) unanswered"));
		if (client != null && client.lastError() != null)
			sender.sendMessage(Style.rowRaw("Last error", Style.BAD + client.lastError()));

		sender.sendMessage(Style.gap());
		usage(sender);
		sender.sendMessage(Style.gap());
	}

	private void usage(CommandSender sender) {
		sender.sendMessage(Style.hint("/sknet resync", "throw the copy away and pull everything"));
		sender.sendMessage(Style.hint("/sknet reconnect", "drop the connection and resume"));
	}

	private static String colour(SyncState state) {
		return switch (state) {
			case READY -> Style.GOOD;
			case SYNCING, CONNECTING -> Style.WARN;
			case DISCONNECTED -> Style.BAD;
		};
	}

	private String version() {
		return plugin.getPluginMeta().getVersion();
	}
}
