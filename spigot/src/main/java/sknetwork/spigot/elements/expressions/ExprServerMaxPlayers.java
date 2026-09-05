package sknetwork.spigot.elements.expressions;

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
import sknetwork.common.RemoteServer;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Server Max Players")
@Description("How many players a server on the network will let in.")
@Example("""
		if size of network players on "lobby" >= max player count of network server "lobby":
			send "&cThe lobby is full."
		""")
@Since("0.2.0")
public class ExprServerMaxPlayers extends SimpleExpression<Integer> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprServerMaxPlayers.class, Integer.class)
						.supplier(ExprServerMaxPlayers::new)
						.addPatterns(
								"[the] (max[imum] player count|[max[imum]] player limit) of network server %string%")
						.build());
	}

	private Expression<String> server;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		server = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	protected Integer @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String name = server.getSingle(event);
		if (plugin == null || name == null)
			return new Integer[0];

		RemoteServer remote = plugin.network().server(name);
		return remote == null ? new Integer[0] : new Integer[] { remote.maxPlayers() };
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Integer> getReturnType() {
		return Integer.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "max player count of network server " + server.toString(event, debug);
	}
}
