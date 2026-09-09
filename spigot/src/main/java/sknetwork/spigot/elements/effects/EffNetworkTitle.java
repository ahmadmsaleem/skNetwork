package sknetwork.spigot.elements.effects;


import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.text.TextComponentUtils;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.common.NetworkTitle;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerAction;
import sknetwork.spigot.NetworkText;
import sknetwork.spigot.SkNetworkSpigot;
import sknetwork.spigot.elements.types.NetworkPlayer;

@Name("Network Title")
@Description("""
		Shows a title to a player anywhere on the network, whichever server they are on.
		The title is styled here and rendered there, so colours and fonts survive the trip. A player nobody is holding is skipped.
		Leave the times out for Minecraft's own defaults.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		send network title "&aWelcome back" with subtitle "&7You were away for a while" to network player "Notch"
		""")
@Example("""
		send network title "&cServer restarting" across the network for 5 seconds with fade in 1 second and fade out 1 second
		""")
@Since("1.0.0")
public class EffNetworkTitle extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffNetworkTitle.class)
				.supplier(EffNetworkTitle::new)
				.addPatterns(
						"send network title %objects% [with subtitle %-objects%] to network player[s] "
								+ "%networkplayers% [for %-timespan%] [with fade[( |-)]in %-timespan%] "
								+ "[and fade[( |-)]out %-timespan%]",
						"send network title %objects% [with subtitle %-objects%] (across|to) [the] network "
								+ "[for %-timespan%] [with fade[( |-)]in %-timespan%] "
								+ "[and fade[( |-)]out %-timespan%]")
				.build());
	}

	private Expression<? extends Component> title;
	private Expression<? extends Component> subtitle;
	private Expression<NetworkPlayer> targets;
	private Expression<Timespan> stay;
	private Expression<Timespan> fadeIn;
	private Expression<Timespan> fadeOut;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		title = TextComponentUtils.asComponentExpression(exprs[0]);
		if (title == null)
			return false;
		if (exprs[1] != null)
			subtitle = TextComponentUtils.asComponentExpression(exprs[1]);

		int next = 2;
		if (matchedPattern == 0)
			targets = (Expression<NetworkPlayer>) exprs[next++];

		stay = (Expression<Timespan>) exprs[next++];
		fadeIn = (Expression<Timespan>) exprs[next++];
		fadeOut = (Expression<Timespan>) exprs[next];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return;

		Component shown = title.getSingle(event);
		if (shown == null)
			return;
		Component under = subtitle == null ? null : subtitle.getSingle(event);

		NetworkTitle payload = new NetworkTitle(
				NetworkText.toJson(shown),
				under == null ? null : NetworkText.toJson(under),
				ticks(fadeIn, event, NetworkTitle.DEFAULT_FADE_IN),
				ticks(stay, event, NetworkTitle.DEFAULT_STAY),
				ticks(fadeOut, event, NetworkTitle.DEFAULT_FADE_OUT));

		PacketOut body = PacketOut.body();
		payload.write(body);

		NetworkTargets.Targets to = NetworkTargets.of(targets, event);
		plugin.playerAction(PlayerAction.TITLE, to.everyone(), to.names(), body.payload());
	}

	private static int ticks(Expression<Timespan> expression, Event event, int fallback) {
		if (expression == null)
			return fallback;
		Timespan span = expression.getSingle(event);
		return span == null ? fallback : (int) Math.min(span.getTicks(), Integer.MAX_VALUE);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "send network title " + title.toString(event, debug)
				+ (targets == null ? " across the network" : " to " + targets.toString(event, debug));
	}
}
