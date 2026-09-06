package sknetwork.spigot;

import java.util.Locale;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import sknetwork.common.Protocol;
import sknetwork.common.Style;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** {@code /sknet}. */
final class SknetCommand implements CommandExecutor {

	private static final String HELP_HEADER = """
			This server's build, and the wire protocol it speaks.
			The proxy and every backend have to agree on the number,
			or the connection is refused at the handshake.""";

	private static final String HELP_STATE = """
			READY means writes are accepted.
			In any other state a write to a network variable is refused,
			though reads keep working from the mirror.""";

	private static final String HELP_SERVER = """
			The name this server goes by on the network.
			It comes from server-name in config.yml, or
			server-<port> when that is left blank.""";

	private static final String HELP_PROXY = """
			Where this server found the proxy, and how long a round
			trip takes. The ping runs every five seconds.""";

	private static final String HELP_STORAGE = """
			The prefix that makes a variable a network variable, and
			the pattern Skript matches it against. The pattern lives
			in the skNetwork block of Skript's config.sk.""";

	private static final String HELP_MIRROR = """
			How many network variables this server holds, and how far
			through the proxy's change log it has read.
			Reads are answered from here, so they cost nothing.""";

	private static final String HELP_APPLIED = """
			Changes from the proxy that reached this server's map.
			A dropped one carried a type this server cannot read,
			usually because Skript or a plugin differs here.""";

	private static final String HELP_REFUSED = """
			Writes that never left this server, because it was not
			synced yet or the name did not carry the prefix.""";

	private static final String HELP_WAITING = """
			Atomic changes still waiting on the proxy. Each one gives
			up after the atomic-timeout in config.yml and comes back
			to the script as refused.""";

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
				header(sender);
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

		header(sender);
		sender.sendMessage(SknetStyle.gap());

		row(sender, "State", HELP_STATE,
				"<" + colour(state) + "><state>"
						+ (state == SyncState.READY ? "" : "<gray>  writes are refused"),
				SknetStyle.text("state", state.toString()));

		row(sender, "Server", HELP_SERVER, "<white><name>",
				SknetStyle.text("name", plugin.serverName()));

		long latency = client == null ? -1 : client.latencyMs();
		row(sender, "Proxy", HELP_PROXY,
				"<white><target>" + (latency < 0 ? "" : "<dark_gray>  <ms>ms"),
				SknetStyle.text("target", client == null ? "-" : client.describeTarget()),
				SknetStyle.text("ms", Style.number(latency)));

		if (SkNetworkStorage.isConfigured())
			row(sender, "Storage", HELP_STORAGE, "<white><prefix><dark_gray>  routing <pattern>",
					SknetStyle.text("prefix", plugin.prefix()),
					SknetStyle.text("pattern", SkNetworkStorage.pattern()));
		else
			row(sender, "Storage", HELP_STORAGE, "<red>NOT CONFIGURED");

		row(sender, "Mirror", HELP_MIRROR, "<white><count> <gray>variables<dark_gray>  seq <seq>",
				SknetStyle.text("count", Style.number(applier == null ? 0 : applier.mirroredCount())),
				SknetStyle.text("seq", Style.number(client == null ? 0 : client.lastSeq())));

		long dropped = applier == null ? 0 : applier.dropped();
		row(sender, "Applied", HELP_APPLIED,
				"<white><applied> <gray>inbound"
						+ (dropped == 0 ? "" : "<dark_gray>  -  <red><dropped> <gray>dropped"),
				SknetStyle.text("applied", Style.number(applier == null ? 0 : applier.applied())),
				SknetStyle.text("dropped", Style.number(dropped)));

		long refused = plugin.droppedWrites();
		if (refused > 0)
			row(sender, "Refused", HELP_REFUSED, "<red><count> <gray>outbound write(s)",
					SknetStyle.text("count", Style.number(refused)));
		if (requests != null && requests.pending() > 0)
			row(sender, "Waiting", HELP_WAITING, "<yellow><count> <gray>atomic change(s) unanswered",
					SknetStyle.text("count", Style.number(requests.pending())));

		sender.sendMessage(SknetStyle.gap());
		usage(sender);
		sender.sendMessage(SknetStyle.gap());
	}

	private void row(CommandSender sender, String label, String help, String value,
			TagResolver... values) {
		sender.sendMessage(SknetStyle.hover(SknetStyle.row(label, value, values), help));
	}

	private void header(CommandSender sender) {
		sender.sendMessage(SknetStyle.hover(
				SknetStyle.header(version(), Protocol.VERSION), HELP_HEADER));
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
