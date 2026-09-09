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
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.common.PlayerProperties;
import sknetwork.spigot.SkNetworkSpigot;
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.NetworkPlayers;

@Name("Network Player Details")
@Description("""
		The ping, address, display name, locale or playtime of somebody anywhere on the network.
		The server holding them reports these, and every other server reads its own copy, so none of this waits on the wire.
		Only what changed is sent, so a ping is at most a few seconds old and moves in steps of 5ms. Playtime counts up on its own between reports.
		Nothing is set for somebody nobody is holding.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		send "%event-networkplayer% is on %ping of network player event-networkplayer%ms"
		""")
@Example("""
		on network player join:
			if playtime of network player event-networkplayer is less than 10 minutes:
				broadcast "&aWelcome %display name of network player event-networkplayer%!"
		""")
@Since("1.0.0")
public class ExprNetworkPlayerProperty extends SimpleExpression<Object> {

	private static final int PING = 0;
	private static final int IP = 1;
	private static final int DISPLAY_NAME = 2;
	private static final int LOCALE = 3;
	private static final int PLAYTIME = 4;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPlayerProperty.class, Object.class)
						.supplier(ExprNetworkPlayerProperty::new)
						.addPatterns(
								"[the] ping of network player[s] %networkplayers%",
								"[the] (ip|address|ip address) of network player[s] %networkplayers%",
								"[the] display name of network player[s] %networkplayers%",
								"[the] locale of network player[s] %networkplayers%",
								"[the] play[ ]time of network player[s] %networkplayers%")
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
	protected Object @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return new Object[0];

		List<Object> found = new ArrayList<>();
		for (NetworkPlayer player : players.getArray(event)) {
			String name = NetworkPlayers.name(player);
			if (name == null)
				continue;

			PlayerProperties details = plugin.network().properties(name);
			if (details == null)
				continue;

			Object value = switch (detail) {
				case PING -> details.ping();
				case IP -> details.ip();
				case DISPLAY_NAME -> details.displayName();
				case LOCALE -> details.locale();
				default -> new Timespan(Timespan.TimePeriod.TICK, Math.max(details.playtimeTicks(), 0));
			};
			if (value != null)
				found.add(value);
		}
		return found.toArray();
	}

	@Override
	public boolean isSingle() {
		return players.isSingle();
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return switch (detail) {
			case PING -> Number.class;
			case PLAYTIME -> Timespan.class;
			default -> String.class;
		};
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String name = switch (detail) {
			case PING -> "ping";
			case IP -> "ip";
			case DISPLAY_NAME -> "display name";
			case LOCALE -> "locale";
			default -> "playtime";
		};
		return name + " of network player " + players.toString(event, debug);
	}
}
