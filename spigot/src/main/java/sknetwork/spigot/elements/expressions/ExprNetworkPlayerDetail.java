package sknetwork.spigot.elements.expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.NetworkPlayers;

@Name("Network Player Name / UUID")
@Description("""
		The name or the UUID of a network player.
		The proxy sends names, so a UUID is filled in from this server's own knowledge of that player and is not set for somebody it has never seen.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		send "%name of network player "Notch"%"
		""")
@Since("0.4.0")
public class ExprNetworkPlayerDetail extends SimpleExpression<String> {

	private static final int NAME = 0;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPlayerDetail.class, String.class)
						.supplier(ExprNetworkPlayerDetail::new)
						.addPatterns(
								"[the] name[s] of network player[s] %networkplayers%",
								"[the] (uuid[s]|unique id[s]) of network player[s] %networkplayers%")
						.build());
	}

	private Expression<NetworkPlayer> players;
	private int detail;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		players = (Expression<NetworkPlayer>) exprs[0];
		detail = matchedPattern;
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		List<String> found = new ArrayList<>();
		for (NetworkPlayer player : players.getArray(event)) {
			String value = detail == NAME ? NetworkPlayers.name(player) : uuid(player);
			if (value != null)
				found.add(value);
		}
		return found.toArray(new String[0]);
	}

	private static String uuid(NetworkPlayer player) {
		UUID resolved = NetworkPlayers.uuid(player);
		return resolved == null ? null : resolved.toString();
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
		return (detail == NAME ? "name" : "uuid") + " of network player "
				+ players.toString(event, debug);
	}
}
