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

@Name("Network Action Bar")
@Description({
		"Shows an action bar to a player anywhere on the network.",
		"Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players"
})
@Example("send network action bar \"&cThe event starts in 10 seconds\" to network players \"Notch\", \"eult\"")
@Since("0.2.0")
public class EffNetworkActionBar extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffNetworkActionBar.class)
				.supplier(EffNetworkActionBar::new)
				.addPatterns(
						"send network action[ ]bar %objects% to network player[s] %strings%",
						"send network action[ ]bar %objects% to [the] [whole] network")
				.build());
	}

	private Expression<? extends Component> text;
	private Expression<String> targets;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		text = TextComponentUtils.asComponentExpression(exprs[0]);
		if (text == null)
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
		for (Component line : text.getArray(event))
			plugin.playerAction(PlayerAction.ACTION_BAR, to, NetworkText.toJson(line));
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "send network action bar " + text.toString(event, debug)
				+ (targets == null ? " to the network" : " to network player " + targets.toString(event, debug));
	}
}
