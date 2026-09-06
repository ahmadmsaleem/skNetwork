package sknetwork.spigot.elements.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Server Of Player")
@Description("""
		Which server a player is on, by name, or nothing when nobody on the network is holding them.
		Use it before moving somebody, so you do not connect them to the server they are already standing on.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		command /find <text>:
			trigger:
				set {_where} to network server of arg-1
				if {_where} is set:
					send "%arg-1% is on %{_where}%."
				else:
					send "%arg-1% is not online."
		""")
@Since("0.2.0")
public class ExprPlayerServer extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprPlayerServer.class, String.class)
						.supplier(ExprPlayerServer::new)
						.addPatterns(
								"network server of [player[s]] %strings%",
								"%strings%'[s] network server")
						.build());
	}

	private Expression<String> players;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		players = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return new String[0];

		List<String> found = new ArrayList<>();
		for (String player : players.getArray(event)) {
			String server = plugin.network().serverOf(player);
			if (server != null)
				found.add(server);
		}
		return found.toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return players.isSingle();
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network server of " + players.toString(event, debug);
	}
}
