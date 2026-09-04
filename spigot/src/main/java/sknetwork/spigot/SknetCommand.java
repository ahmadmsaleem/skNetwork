package sknetwork.spigot;

import java.util.Locale;

import sknetwork.common.Protocol;
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
			case "resync" -> {
				resync(sender);
				return true;
			}
			case "reconnect" -> {
				reconnect(sender);
				return true;
			}
			case "" -> {
				status(sender);
				return true;
			}
			default -> {
				sender.sendMessage("skNetwork: no such subcommand '" + args[0] + "'.");
				sender.sendMessage("  /sknet             state, proxy, mirror, latency");
				sender.sendMessage("  /sknet resync      drop the mirror and pull everything again");
				sender.sendMessage("  /sknet reconnect   drop the connection and resume where it left off");
				return true;
			}
		}
	}


	/** Throws the mirror away and pulls the whole map back, rather than resuming. */
	private void resync(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		if (client == null || applier == null) {
			sender.sendMessage("skNetwork: network variables are not running on this server.");
			return;
		}

		applier.requestFullSnapshot();
		client.dropConnection();
		sender.sendMessage("skNetwork: dropped the connection and asked for a full snapshot. "
				+ "Reads keep working from the mirror until it lands.");
	}

	/** The cheap one: the proxy replays only what was missed. */
	private void reconnect(CommandSender sender) {
		ProxyClient client = plugin.client();
		if (client == null) {
			sender.sendMessage("skNetwork: network variables are not running on this server.");
			return;
		}

		client.dropConnection();
		sender.sendMessage("skNetwork: dropped the connection, resuming from seq "
				+ client.lastSeq() + ". Use /sknet resync to pull everything instead.");
	}

	private void status(CommandSender sender) {
		ProxyClient client = plugin.client();
		DeltaApplier applier = plugin.applier();
		AtomicRequests requests = plugin.requests();
		SyncState state = client == null ? SyncState.DISCONNECTED : client.state();

		sender.sendMessage("skNetwork " + plugin.getPluginMeta().getVersion()
				+ " (protocol " + Protocol.VERSION + ")");
		sender.sendMessage("  state:    " + state
				+ (state == SyncState.READY ? "" : "  writes are refused"));
		sender.sendMessage("  proxy:    " + (client == null ? "-" : client.describeTarget())
				+ (client == null || client.latencyMs() < 0 ? "" : "   latency: " + client.latencyMs() + "ms"));
		sender.sendMessage("  prefix:   " + plugin.prefix() + "   storage: "
				+ (SkNetworkStorage.isConfigured()
						? "routing " + SkNetworkStorage.pattern()
						: "NOT CONFIGURED"));
		sender.sendMessage("  seq:      " + (client == null ? 0 : client.lastSeq())
				+ "   mirror: " + (applier == null ? 0 : applier.mirroredCount()) + " variable(s)");
		sender.sendMessage("  applied:  " + (applier == null ? 0 : applier.applied()) + " inbound change(s)"
				+ (applier == null || applier.dropped() == 0 ? "" : ", " + applier.dropped() + " dropped"));
		sender.sendMessage("  dropped:  " + plugin.droppedWrites() + " outbound write(s)");
		if (requests != null && requests.pending() > 0)
			sender.sendMessage("  waiting:  " + requests.pending() + " atomic change(s) not answered yet");
		if (client != null && client.lastError() != null)
			sender.sendMessage("  last error: " + client.lastError());
		sender.sendMessage("  /sknet resync   to pull the whole map again");
	}
}
