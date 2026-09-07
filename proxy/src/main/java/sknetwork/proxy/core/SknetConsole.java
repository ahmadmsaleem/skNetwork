package sknetwork.proxy.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import sknetwork.common.Protocol;
import sknetwork.common.Style;

public final class SknetConsole {

	private static final int DUMP_LIMIT = 40;

	private static final String INDENT = Style.MUTED + "│   ";

	public static final String COMMAND = "/sknetproxy";

	public static void run(NetworkServer server, String version, String[] args, Consumer<String> reply) {
		if (server == null) {
			reply.accept(Style.BAD + "skNetwork is not running.");
			return;
		}

		String command = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
		switch (command) {
			case "push" -> push(server, reply);
			case "dump" -> dump(server, args, reply);
			case "" -> status(server, version, reply);
			case "resync", "reconnect" -> {
				reply.accept(Style.header(version, Protocol.VERSION));
				reply.accept(Style.gap());
				reply.accept(Style.note("'" + command + "' belongs to a backend server."));
				reply.accept(Style.hint("/sknet " + command, "run this on the backend instead"));
				reply.accept(Style.gap());
			}
			default -> {
				reply.accept(Style.header(version, Protocol.VERSION));
				reply.accept(Style.gap());
				reply.accept(Style.note("no such subcommand '" + args[0] + "'"));
				reply.accept(Style.gap());
				usage(reply);
				reply.accept(Style.gap());
			}
		}
	}

	private static void push(NetworkServer server, Consumer<String> reply) {
		if (!server.sharingScripts()) {
			reply.accept(Style.note("script sharing is off. Turn on 'scripts.enabled' in config.yml."));
			return;
		}
		int sent = server.push(true);
		reply.accept(Style.rowRaw("Pushed", Style.VALUE + sent + Style.LABEL + " server(s)"));
		reply.accept(Style.note("results follow in the console"));
	}

	private static void status(NetworkServer server, String version, Consumer<String> reply) {
		reply.accept(Style.header(version, Protocol.VERSION));
		reply.accept(Style.gap());

		reply.accept(Style.row("Variables", Style.number(server.variableCount())
				+ Style.dim("  seq " + Style.number(server.sequence()))));
		reply.accept(Style.row("Log", server.logSummary()));
		reply.accept(Style.row("Scripts", server.scriptSummary()));

		reply.accept(Style.gap());
		backends(server, reply);

		reply.accept(Style.gap());
		usage(reply);
		reply.accept(Style.gap());
	}

	private static void backends(NetworkServer server, Consumer<String> reply) {
		List<NetworkServer.Backend> backends = server.backends();
		if (backends.isEmpty()) {
			reply.accept(Style.rowRaw("Backends", Style.WARN + "none connected"));
			reply.accept(Style.note("check 'proxy.host' and 'proxy.token' on each backend"));
			return;
		}

		reply.accept(Style.rowRaw("Backends", Style.GOOD + backends.size()
				+ Style.LABEL + (backends.size() == 1 ? " server" : " servers")));

		int width = 0;
		for (NetworkServer.Backend backend : backends)
			width = Math.max(width, backend.name().length());

		for (NetworkServer.Backend backend : backends) {
			reply.accept(INDENT + Style.BRAND + pad(backend.name(), width)
					+ Style.LABEL + "  " + backend.address()
					+ Style.dim("  Skript " + backend.skriptVersion())
					+ Style.dim("  seq " + Style.number(backend.lastSeq()))
					+ "  " + progress(backend));

			if (backend.usePlayerUuids() != server.usePlayerUuids())
				reply.accept(INDENT + " ".repeat(width) + "  " + Style.BAD + "player UUIDs "
						+ spelling(backend.usePlayerUuids()) + " here, proxy expects "
						+ spelling(server.usePlayerUuids())
						+ Style.LABEL + " - two keys for the same player");
		}
	}

	private static String progress(NetworkServer.Backend backend) {
		if (!backend.ready())
			return Style.WARN + "syncing";

		String state = backend.behind() == 0
				? Style.GOOD + "ok"
				: Style.WARN + Style.number(backend.behind()) + " behind";
		return backend.queuedBytes() == 0
				? state
				: state + Style.dim("  " + Style.bytes(backend.queuedBytes()) + " queued");
	}

	private static String spelling(boolean usePlayerUuids) {
		return usePlayerUuids ? "on" : "off";
	}

	private static String pad(String value, int width) {
		return value.length() >= width ? value : value + " ".repeat(width - value.length());
	}

	private static void usage(Consumer<String> reply) {
		reply.accept(Style.hint(COMMAND + " push", "send scripts now"));
		reply.accept(Style.hint(COMMAND + " dump <name>", "look up a variable, '*' is a wildcard"));
	}


	private static void dump(NetworkServer server, String[] args, Consumer<String> reply) {
		if (args.length < 2) {
			reply.accept(Style.header("", Protocol.VERSION));
			reply.accept(Style.gap());
			reply.accept(Style.note("give a name to look up, where '*' matches anything"));
			reply.accept(Style.hint(COMMAND + " dump coins::*", "one group"));
			reply.accept(Style.hint(COMMAND + " dump *", "everything"));
			reply.accept(Style.note("names are as the network knows them, so without the prefix"));
			reply.accept(Style.gap());
			return;
		}

		String glob = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		NetworkServer.Dump dump = server.dump(glob, DUMP_LIMIT);

		if (dump.total() == 0) {
			reply.accept(Style.note("nothing matches " + Style.VALUE + glob));
			if (glob.indexOf('*') < 0)
				reply.accept(Style.note("add a '*' to match more than one exact name"));
			return;
		}

		reply.accept(Style.rowRaw("Matched", Style.VALUE + Style.number(dump.total())
				+ Style.LABEL + " for " + Style.VALUE + glob));
		reply.accept(Style.gap());
		for (NetworkServer.DumpLine line : dump.lines())
			reply.accept(INDENT + Style.BRAND + line.name()
					+ Style.MUTED + " = " + Style.VALUE + line.value()
					+ Style.dim("  " + line.type() + ", seq " + Style.number(line.seq())));

		if (dump.total() > dump.lines().size()) {
			reply.accept(Style.gap());
			reply.accept(Style.note("and " + Style.number(dump.total() - dump.lines().size())
					+ " more. Narrow the pattern."));
		}
		reply.accept(Style.gap());
	}

	private SknetConsole() {
	}
}
