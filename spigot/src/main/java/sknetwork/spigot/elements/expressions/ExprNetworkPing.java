package sknetwork.spigot.elements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
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
import sknetwork.common.PingField;
import sknetwork.common.PingSettings;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Ping Motd, Max Players And Player Count")
@Description("""
		What the proxy answers server list pings with, for everyone looking at the network in their server list.
		Setting one sends it to the proxy, which answers every ping from then on without asking any server. Reads come from this server's own copy.
		A value set here outlives a proxy restart. Reset one and the proxy answers that part of the ping exactly as it would without skNetwork.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		on network sync:
			set network motd to "&aMy Network &7| &fnow with skyblock"
			set network max players to 500
		""")
@Example("""
		every 10 seconds:
			set network player count to size of all network players
		""")
@Example("""
		command /clearmotd:
			trigger:
				reset network motd
		""")
@Since("1.0.0")
public class ExprNetworkPing extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprNetworkPing.class, Object.class)
						.supplier(ExprNetworkPing::new)
						.addPatterns(
								"[the] network (motd|message of the day)",
								"[the] network max[imum] players",
								"[the] network player count")
						.build());
	}

	private PingField field;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		field = PingField.byId((byte) matchedPattern);
		return true;
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return new Object[0];

		PingSettings ping = plugin.ping();
		if (field == PingField.MOTD)
			return ping.motd() == null ? new Object[0] : new Object[] { ping.motd() };

		Integer number = ping.number(field);
		return number == null ? new Object[0] : new Object[] { number };
	}

	@Override
	public Class<?> @Nullable [] acceptChange(@NotNull ChangeMode mode) {
		return switch (mode) {
			case SET -> new Class<?>[] { Object.class };
			case RESET, DELETE -> new Class<?>[0];
			default -> null;
		};
	}

	@Override
	public void change(@NotNull Event event, Object @Nullable [] delta, @NotNull ChangeMode mode) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return;

		if (mode != ChangeMode.SET) {
			plugin.setPing(field, null);
			return;
		}
		if (delta == null || delta.length == 0 || delta[0] == null)
			return;

		plugin.setPing(field, encode(delta[0]));
	}

	private String encode(Object value) {
		if (field != PingField.MOTD)
			return value instanceof Number number
					? Integer.toString(number.intValue())
					: String.valueOf(value).trim();

		return String.valueOf(value).replace('&', '\u00a7');
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return field == PingField.MOTD ? String.class : Number.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return switch (field) {
			case MOTD -> "the network motd";
			case MAX_PLAYERS -> "the network max players";
			case PLAYER_COUNT -> "the network player count";
		};
	}
}
