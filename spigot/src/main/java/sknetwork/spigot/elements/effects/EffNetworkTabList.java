package sknetwork.spigot.elements.effects;


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
import sknetwork.common.NetworkTabList;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerAction;
import sknetwork.spigot.NetworkText;
import sknetwork.spigot.SkNetworkSpigot;
import sknetwork.spigot.elements.types.NetworkPlayer;

@Name("Network Tab List Header And Footer")
@Description("""
		Sets the tab list header, footer or both for a player anywhere on the network.
		Only the part you name is changed, so setting a footer leaves the header alone. A player nobody is holding is skipped.
		The server holding them may set its own afterwards, which replaces this.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		set network tab list header of network player "Notch" to "&aMy Network"
		""")
@Example("""
		set network tab list header to "&aMy Network" and footer to "&7play.example.com" across the network
		""")
@Since("1.0.0")
public class EffNetworkTabList extends Effect {

	private static final int HEADER = 0;
	private static final int FOOTER = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffNetworkTabList.class)
				.supplier(EffNetworkTabList::new)
				.addPatterns(
						"set network tab[ ]list header of network player[s] %networkplayers% to %objects%",
						"set network tab[ ]list footer of network player[s] %networkplayers% to %objects%",
						"set network tab[ ]list header to %objects% and footer to %objects% "
								+ "of network player[s] %networkplayers%",
						"set network tab[ ]list header to %objects% (across|to) [the] network",
						"set network tab[ ]list footer to %objects% (across|to) [the] network",
						"set network tab[ ]list header to %objects% and footer to %objects% "
								+ "(across|to) [the] network")
				.build());
	}

	private Expression<? extends Component> header;
	private Expression<? extends Component> footer;
	private Expression<NetworkPlayer> targets;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		switch (matchedPattern) {
			case 0, 1 -> {
				targets = (Expression<NetworkPlayer>) exprs[0];
				Expression<? extends Component> text = TextComponentUtils.asComponentExpression(exprs[1]);
				if (matchedPattern == HEADER)
					header = text;
				else
					footer = text;
			}
			case 2 -> {
				header = TextComponentUtils.asComponentExpression(exprs[0]);
				footer = TextComponentUtils.asComponentExpression(exprs[1]);
				targets = (Expression<NetworkPlayer>) exprs[2];
			}
			case 3 -> header = TextComponentUtils.asComponentExpression(exprs[0]);
			case 4 -> footer = TextComponentUtils.asComponentExpression(exprs[0]);
			default -> {
				header = TextComponentUtils.asComponentExpression(exprs[0]);
				footer = TextComponentUtils.asComponentExpression(exprs[1]);
			}
		}
		return header != null || footer != null;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return;

		NetworkTabList payload = new NetworkTabList(json(header, event), json(footer, event));
		if (payload.header() == null && payload.footer() == null)
			return;

		PacketOut body = PacketOut.body();
		payload.write(body);

		NetworkTargets.Targets to = NetworkTargets.of(targets, event);
		plugin.playerAction(PlayerAction.TAB_LIST, to.everyone(), to.names(), body.payload());
	}

	private static String json(Expression<? extends Component> expression, Event event) {
		if (expression == null)
			return null;
		Component value = expression.getSingle(event);
		return value == null ? null : NetworkText.toJson(value);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "set network tab list of "
				+ (targets == null ? "the network" : targets.toString(event, debug));
	}
}
