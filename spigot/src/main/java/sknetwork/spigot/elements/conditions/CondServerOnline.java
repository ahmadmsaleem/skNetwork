package sknetwork.spigot.elements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Server Is Online")
@Description({
		"Checks whether a server is connected to the proxy right now.",
		"A server that is running but has not finished syncing does not count, because nothing "
				+ "sent to it would arrive yet.",
		"Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players"
})
@Example("""
		if network server "survival" is online:
			connect network player "%player%" to "survival"
		else:
			send "&cSurvival is down."
		""")
@Since("0.2.0")
public class CondServerOnline extends Condition {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondServerOnline.class)
				.supplier(CondServerOnline::new)
				.addPatterns(
						"network server[s] %strings% (is|are) online",
						"network server[s] %strings% (isn't|is not|aren't|are not) online")
				.build());
	}

	private Expression<String> servers;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		servers = (Expression<String>) exprs[0];
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return isNegated();

		return isNegated() != servers.check(event, server -> plugin.network().isOnline(server));
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network server " + servers.toString(event, debug)
				+ (isNegated() ? " is not online" : " is online");
	}
}
