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
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.text.TextComponentUtils;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.common.PlayerAction;
import sknetwork.spigot.NetworkText;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Message")
@Description({
		"Sends a message to a player anywhere on the network, whichever server they are on.",
		"The message is styled here and rendered there, so colours, hover text and clickable "
				+ "parts all survive the trip.",
		"A player nobody is holding is skipped. Nothing is queued for someone offline."
})
@Example("send network message \"&aYou were paid 50 coins.\" to network player \"Notch\"")
@Example("broadcast \"&e%player% just won the event!\" across the network")
@Since("0.2.0")
public class EffNetworkMessage extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffNetworkMessage.class)
				.supplier(EffNetworkMessage::new)
				.addPatterns(
						"send network message %objects% to network player[s] %strings%",
						"broadcast %objects% (across|to) [the] network")
				.build());
	}

	private Expression<? extends Component> messages;
	private Expression<String> targets;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		messages = TextComponentUtils.asComponentExpression(exprs[0]);
		if (messages == null)
			return false;
		if (matchedPattern == 0)
			targets = (Expression<String>) exprs[1];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return;

		List<String> to = targets == null ? List.of() : List.of(targets.getArray(event));
		for (Component message : messages.getArray(event))
			plugin.playerAction(PlayerAction.MESSAGE, to, NetworkText.toJson(message));
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		if (targets == null)
			return "broadcast " + messages.toString(event, debug) + " across the network";
		return "send network message " + messages.toString(event, debug)
				+ " to network player " + targets.toString(event, debug);
	}
}
