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

@Name("Network Players")
@Description({
		"The names of everyone online across the whole network, or on the servers you name.",
		"This replaces keeping your own list in a variable. The proxy rebuilds it whenever a "
				+ "server joins or leaves, so a server that crashes takes its players with it "
				+ "instead of leaving them listed forever.",
		"Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players"
})
@Example("send \"%size of all network players% online\"")
@Example("send \"On the hubs: %network players on \"lobby\", \"lobby2\"%\"")
@Since("0.2.0")
public class ExprNetworkPlayers extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPlayers.class, String.class)
						.supplier(ExprNetworkPlayers::new)
						.addPatterns(
								"[(all [[of] the]|the)] network players (on|of) [server[s]] %strings%",
								"[(all [[of] the]|the)] network players")
						.build());
	}

	private Expression<String> servers;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		if (matchedPattern == 0)
			servers = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return new String[0];

		if (servers == null)
			return plugin.network().players(null).toArray(new String[0]);

		List<String> names = new ArrayList<>();
		for (String server : servers.getArray(event))
			names.addAll(plugin.network().players(server));
		return names.toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network players" + (servers == null ? "" : " on " + servers.toString(event, debug));
	}
}
