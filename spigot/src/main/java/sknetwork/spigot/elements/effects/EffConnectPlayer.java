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
import sknetwork.common.PlayerAction;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Connect Network Player")
@Description({
		"Moves a player to another server on the proxy.",
		"This is the one thing the proxy has to do itself, so it is the only syntax here that "
				+ "stops working when `players` is off in the proxy config.",
		"Nothing happens if that player is not on the network, or the server name is not one the "
				+ "proxy knows."
})
@Example("connect network player \"%player%\" to \"survival\"")
@Since("0.2.0")
public class EffConnectPlayer extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffConnectPlayer.class)
				.supplier(EffConnectPlayer::new)
				.addPatterns("(connect|send) network player[s] %strings% to [server] %string%")
				.build());
	}

	private Expression<String> players;
	private Expression<String> target;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		players = (Expression<String>) exprs[0];
		target = (Expression<String>) exprs[1];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String server = target.getSingle(event);
		if (plugin == null || server == null)
			return;

		plugin.playerAction(PlayerAction.CONNECT, List.of(players.getArray(event)), server);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "connect network player " + players.toString(event, debug)
				+ " to " + target.toString(event, debug);
	}
}
