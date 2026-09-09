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
import sknetwork.spigot.elements.events.NetworkPlayerEvent;
import sknetwork.spigot.elements.events.NetworkPlayerJoinEvent;
import sknetwork.spigot.elements.events.NetworkPlayerQuitEvent;
import sknetwork.spigot.elements.events.NetworkServerSwitchEvent;

@Name("Previous / New Network Server")
@Description("""
		Inside `on network player join`, `on network player quit` and `on network server switch`, the servers either side of the move.
		A join has no previous server and a quit has no new one, so the one that does not apply is unset.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
		""")
@Example("""
		on network server switch:
			broadcast "%event-networkplayer% went from %the previous network server% to %the new network server%"
		""")
@Since("1.0.0")
public class ExprEventNetworkServer extends SimpleExpression<String> {

	private static final int PREVIOUS = 0;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprEventNetworkServer.class, String.class)
						.supplier(ExprEventNetworkServer::new)
						.addPatterns(
								"[the] (previous|old|past) network server",
								"[the] (new|current) network server")
						.build());
	}

	private int part;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		part = matchedPattern;
		return getParser().isCurrentEvent(NetworkPlayerJoinEvent.class,
				NetworkPlayerQuitEvent.class, NetworkServerSwitchEvent.class);
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		if (!(event instanceof NetworkPlayerEvent moved))
			return new String[0];

		String server = part == PREVIOUS ? moved.previousServer() : moved.newServer();
		return server == null ? new String[0] : new String[] { server };
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return part == PREVIOUS ? "the previous network server" : "the new network server";
	}
}
