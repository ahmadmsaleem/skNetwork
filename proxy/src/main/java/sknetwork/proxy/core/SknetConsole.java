package sknetwork.proxy.core;

import java.util.function.Consumer;

import sknetwork.common.Protocol;

/**
 * The body of {@code /sknet} on the proxy, so BungeeCord and Velocity print the
 * same thing. Each platform only has to hand over a way to send a line back.
 */
public final class SknetConsole {

	/** Enough to read a tree, few enough to not scroll a console away. */
	private static final int DUMP_LIMIT = 40;

	public static void run(NetworkServer server, String version, String[] args, Consumer<String> reply) {
		if (server == null) {
			reply.accept("skNetwork is not running.");
			return;
		}

		String command = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
		switch (command) {
			case "push" -> {
				int sent = server.push(true);
				reply.accept("skNetwork: pushed to " + sent + " server(s), results follow in the console.");
			}
			case "dump" -> dump(server, args, reply);
			case "" -> status(server, version, reply);
			default -> {
				reply.accept("skNetwork: no such subcommand '" + args[0] + "'.");
				reply.accept("  /sknet             state, variable count, script manifest");
				reply.accept("  /sknet push        send scripts now");
				reply.accept("  /sknet dump <pattern>   look up variables by name");
			}
		}
	}

	private static void status(NetworkServer server, String version, Consumer<String> reply) {
		reply.accept("skNetwork " + version + " (protocol " + Protocol.VERSION + ")");
		reply.accept("  backends:  " + server.connectionCount());
		reply.accept("  variables: " + server.variableCount() + " at seq " + server.sequence());
		reply.accept("  scripts:   " + server.scriptSummary());
		reply.accept("  /sknet push          to send scripts now");
		reply.accept("  /sknet dump <name>   to look up a variable, '*' is a wildcard");
	}


	private static void dump(NetworkServer server, String[] args, Consumer<String> reply) {
		if (args.length < 2) {
			reply.accept("skNetwork: /sknet dump <pattern>, where '*' is a wildcard.");
			reply.accept("  /sknet dump coins::*      one tree");
			reply.accept("  /sknet dump *             everything");
			reply.accept("  Names are as the network knows them, so without the '?' prefix.");
			return;
		}

		String glob = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
		NetworkServer.Dump dump = server.dump(glob, DUMP_LIMIT);

		if (dump.total() == 0) {
			reply.accept("skNetwork: nothing matches " + glob
					+ (glob.indexOf('*') < 0 ? " - add a '*' to match more than one exact name." : "."));
			return;
		}

		reply.accept("skNetwork: " + dump.total() + " variable(s) match " + glob
				+ (dump.total() > dump.lines().size() ? ", first " + dump.lines().size() + ":" : ":"));
		for (NetworkServer.DumpLine line : dump.lines())
			reply.accept("  " + line.name() + " = " + line.value()
					+ "  (" + line.type() + ", seq " + line.seq() + ")");

		if (dump.total() > dump.lines().size())
			reply.accept("  ... and " + (dump.total() - dump.lines().size()) + " more. Narrow the pattern.");
	}

	private SknetConsole() {
	}
}
