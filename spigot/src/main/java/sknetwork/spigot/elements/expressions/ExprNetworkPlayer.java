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
import sknetwork.spigot.elements.types.NetworkPlayer;

@Name("Network Player")
@Description("""
		Somebody on the network, from a name, a UUID or a real player.
		Used in a variable name it is spelled the way Skript spells a player, so `{?coins::%network player "Notch"%}` is the same key a script on the server holding them writes.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		set {_who} to network player "Notch"
		send network message "&aHello." to {_who}
		""")
@Since("0.4.0")
public class ExprNetworkPlayer extends SimpleExpression<NetworkPlayer> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPlayer.class, NetworkPlayer.class)
						.supplier(ExprNetworkPlayer::new)
						.addPatterns("network player[s] %networkplayers%")
						.build());
	}

	private Expression<NetworkPlayer> players;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		players = (Expression<NetworkPlayer>) exprs[0];
		return true;
	}

	@Override
	protected NetworkPlayer @NotNull [] get(@NotNull Event event) {
		return players.getArray(event);
	}

	@Override
	public boolean isSingle() {
		return players.isSingle();
	}

	@Override
	public @NotNull Class<? extends NetworkPlayer> getReturnType() {
		return NetworkPlayer.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network player " + players.toString(event, debug);
	}
}
