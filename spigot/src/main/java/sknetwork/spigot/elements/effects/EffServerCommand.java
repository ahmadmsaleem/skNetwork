package sknetwork.spigot.elements.effects;

import java.util.List;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Console Command")
@Description("""
		Runs a command on another server's console.
		Off unless `remote-commands` is turned on in the proxy config. Anyone who can write a script on one backend would otherwise have console on every backend, so the proxy refuses and says so in its log.
		Leave the server out to run it everywhere, including here.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		execute command "save-all" on network server "survival"
		""")
@Example("""
		execute command "whitelist reload" on the network
		""")
@Since("0.2.0")
public class EffServerCommand extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffServerCommand.class)
				.supplier(EffServerCommand::new)
				.addPatterns(
						"execute [console] command %string% on network server[s] %strings%",
						"execute [console] command %string% on [the] [whole] network")
				.build());
	}

	private Expression<String> command;
	private Expression<String> servers;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		command = (Expression<String>) exprs[0];
		if (matchedPattern == 0)
			servers = (Expression<String>) exprs[1];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String line = command.getSingle(event);
		if (plugin == null || line == null)
			return;

		plugin.consoleCommand(servers == null ? List.of() : List.of(servers.getArray(event)), line);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "execute command " + command.toString(event, debug)
				+ (servers == null ? " on the network" : " on network server " + servers.toString(event, debug));
	}
}
