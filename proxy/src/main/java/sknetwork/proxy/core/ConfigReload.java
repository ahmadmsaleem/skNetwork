package sknetwork.proxy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ConfigReload {

	record Report(List<String> applied, List<String> restartNeeded, List<String> notes,
			String error) {

		static Report failed(String error) {
			return new Report(List.of(), List.of(), List.of(), error);
		}

		boolean failed() {
			return error != null;
		}

		boolean nothingChanged() {
			return applied.isEmpty() && restartNeeded.isEmpty();
		}
	}

	static Report apply(NetworkServer server, ProxySettings fresh) {
		ProxySettings live = server.settings();
		ProxySettings booted = server.bootSettings();
		if (live == null || booted == null)
			return Report.failed("this proxy was started without recording its settings");

		List<String> applied = new ArrayList<>();
		List<String> notes = new ArrayList<>();

		flags(server, live, fresh, applied, notes);
		noPersist(server, live, fresh, applied, notes);
		scripts(server, live, fresh, applied, notes);

		server.applied(fresh);
		return new Report(applied, restartNeeded(booted, fresh), notes, null);
	}

	private static void flags(NetworkServer server, ProxySettings live, ProxySettings fresh,
			List<String> applied, List<String> notes) {
		if (live.debug() != fresh.debug()) {
			server.debugEnabled(fresh.debug());
			applied.add("debug");
		}

		boolean playersMoved = live.players() != fresh.players();
		boolean commandsMoved = live.remoteCommands() != fresh.remoteCommands();
		if (!playersMoved && !commandsMoved)
			return;

		server.features(fresh.players(), fresh.remoteCommands());

		if (playersMoved) {
			applied.add("players");
			server.refreshState();
			notes.add(fresh.players()
					? "player features are on: every backend has been sent the network state"
					: "player features are off: every backend has been told to empty its player list");
		}

		if (commandsMoved) {
			applied.add("remote-commands");
			if (fresh.remoteCommands())
				server.log().warn("'remote-commands' was turned on by a reload. Any script on any "
						+ "backend can now run a console command on every other backend.");
			notes.add(fresh.remoteCommands()
					? "remote commands are on: a script on any backend now has console on all of them"
					: "remote commands are off: further attempts are refused and logged");
		}
	}

	private static void noPersist(NetworkServer server, ProxySettings live, ProxySettings fresh,
			List<String> applied, List<String> notes) {
		if (live.noPersist().equals(fresh.noPersist()))
			return;

		NamePatterns patterns = NamePatterns.of(fresh.noPersist());
		server.noPersist(patterns);
		applied.add("no-persist");

		if (!server.persists()) {
			notes.add("'log' is none, so nothing was being written to disk either way");
			return;
		}
		notes.add(patterns.isEmpty()
				? "rewrote " + server.logName() + " from memory: nothing is held back from it now"
				: "rewrote " + server.logName() + " from memory against " + patterns.size()
						+ " pattern(s), and the .bak with it, so a matching value is off the disk");
		notes.add("backends keep these variables in memory, which is what no-persist means");
	}

	private static void scripts(NetworkServer server, ProxySettings live, ProxySettings fresh,
			List<String> applied, List<String> notes) {
		boolean groupsMoved = !live.groups().equals(fresh.groups());
		boolean limitsMoved = live.maxFileBytes() != fresh.maxFileBytes()
				|| live.maxTotalBytes() != fresh.maxTotalBytes();
		if (!groupsMoved && !limitsMoved)
			return;

		List<String> moved = new ArrayList<>();
		if (groupsMoved)
			moved.add("groups");
		if (live.maxFileBytes() != fresh.maxFileBytes())
			moved.add("max-file-kb");
		if (live.maxTotalBytes() != fresh.maxTotalBytes())
			moved.add("max-total-mb");

		ScriptLibrary library = server.scripts();
		if (library == null) {
			notes.add("script distribution is off, so " + String.join(", ", moved)
					+ " will only take effect once 'scripts.enabled' is on and the proxy restarts");
			return;
		}

		if (limitsMoved)
			library.limits(fresh.maxFileBytes(), fresh.maxTotalBytes());

		if (groupsMoved) {
			ServerGroups groups = new ServerGroups(fresh.groups());
			for (String problem : groups.problems())
				server.log().warn(problem);
			library.regroup(groups);
		}

		applied.addAll(moved);

		int pushed = server.push(true);
		notes.add(pushed == 0
				? "no backend is connected, so each one picks up manifest " + library.version()
						+ " when it next connects"
				: "pushed manifest " + library.version() + " to " + pushed + " backend(s): each "
						+ "fetches what it gained and deletes what it lost");
	}

	private static List<String> restartNeeded(ProxySettings booted, ProxySettings fresh) {
		List<String> pending = new ArrayList<>();
		if (!Objects.equals(booted.bind(), fresh.bind()))
			pending.add("bind");
		if (booted.port() != fresh.port())
			pending.add("port");
		if (!Objects.equals(booted.token(), fresh.token()))
			pending.add("token");
		if (!Objects.equals(booted.logName(), fresh.logName()))
			pending.add("log");
		if (booted.flushIntervalMs() != fresh.flushIntervalMs())
			pending.add("flush-interval");
		if (Double.compare(booted.compactRatio(), fresh.compactRatio()) != 0)
			pending.add("compact-when");
		if (booted.replayBuffer() != fresh.replayBuffer())
			pending.add("replay-buffer");
		if (booted.scriptsEnabled() != fresh.scriptsEnabled())
			pending.add("scripts.enabled");
		if (booted.usePlayerUuids() != fresh.usePlayerUuids())
			pending.add("use player UUIDs in variable names");
		return pending;
	}

	private ConfigReload() {
	}
}
