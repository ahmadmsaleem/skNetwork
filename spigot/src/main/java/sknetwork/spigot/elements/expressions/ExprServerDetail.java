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
import sknetwork.common.RemoteServer;
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Server Details")
@Description({
		"The message of the day or the Minecraft version a server on the network reports.",
		"Each server reports its own, so this is what that server actually holds rather than what "
				+ "the proxy was configured to expect."
})
@Example("send \"survival runs %version of network server \"survival\"%\"")
@Since("0.2.0")
public class ExprServerDetail extends SimpleExpression<String> {

	private static final int MOTD = 0;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprServerDetail.class, String.class)
						.supplier(ExprServerDetail::new)
						.addPatterns(
								"[the] (message of the day|motd) of network server %string%",
								"[the] version of network server %string%")
						.build());
	}

	private Expression<String> server;
	private int detail;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		server = (Expression<String>) exprs[0];
		detail = matchedPattern;
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String name = server.getSingle(event);
		if (plugin == null || name == null)
			return new String[0];

		RemoteServer remote = plugin.network().server(name);
		if (remote == null)
			return new String[0];
		return new String[] { detail == MOTD ? remote.motd() : remote.version() };
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
		return (detail == MOTD ? "motd" : "version") + " of network server "
				+ server.toString(event, debug);
	}
}
