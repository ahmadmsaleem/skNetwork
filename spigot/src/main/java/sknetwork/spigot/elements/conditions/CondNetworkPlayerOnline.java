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
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.NetworkPlayers;

@Name("Network Player Is Online")
@Description("""
		Checks whether somebody is online anywhere on the network, not just on this server.
		Answered from the player list the proxy pushes, so it stays right while this server holds nobody.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		if network player "Notch" is online:
			send network message "&aWelcome back." to network player "Notch"
		""")
@Since("1.0.0")
public class CondNetworkPlayerOnline extends Condition {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondNetworkPlayerOnline.class)
				.supplier(CondNetworkPlayerOnline::new)
				.addPatterns(
						"network player[s] %networkplayers% (is|are) online",
						"network player[s] %networkplayers% (isn't|is not|aren't|are not) online")
				.build());
	}

	private Expression<NetworkPlayer> players;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		players = (Expression<NetworkPlayer>) exprs[0];
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return isNegated();

		return isNegated() != players.check(event, player -> {
			String name = NetworkPlayers.name(player);
			return name != null && plugin.network().serverOf(name) != null;
		});
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network player " + players.toString(event, debug)
				+ (isNegated() ? " is not online" : " is online");
	}
}
