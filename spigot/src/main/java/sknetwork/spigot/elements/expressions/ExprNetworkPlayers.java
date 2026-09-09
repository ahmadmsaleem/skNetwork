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
import sknetwork.spigot.elements.types.NetworkPlayer;

@Name("Network Players")
@Description("""
		Everyone online across the whole network, or on the servers you name, as network players.
		This replaces keeping your own list in a variable. The proxy rebuilds it whenever a server joins or leaves, so a server that crashes takes its players with it instead of leaving them listed forever.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		send "%size of all network players% online"
		""")
@Example("""
		send "On the hubs: %network players on "lobby" and "lobby2"%"
		""")
@Since("0.2.0")
public class ExprNetworkPlayers extends SimpleExpression<NetworkPlayer> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPlayers.class, NetworkPlayer.class)
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
	protected NetworkPlayer @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return new NetworkPlayer[0];

		if (servers == null)
			return plugin.network().networkPlayers(null).toArray(new NetworkPlayer[0]);

		List<NetworkPlayer> found = new ArrayList<>();
		for (String server : servers.getArray(event))
			found.addAll(plugin.network().networkPlayers(server));
		return found.toArray(new NetworkPlayer[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public @NotNull Class<? extends NetworkPlayer> getReturnType() {
		return NetworkPlayer.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network players" + (servers == null ? "" : " on " + servers.toString(event, debug));
	}
}
