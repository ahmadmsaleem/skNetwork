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

@Name("Network Server Whitelist")
@Description("""
		The names on another server's whitelist.
		That server reports its own, so this is the list it will actually enforce, not a copy kept somewhere else.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		set {_allowed::*} to whitelisted players of network server "survival"
		if "%player%" is not {_allowed::*}:
			send "&cYou are not whitelisted on survival."
		""")
@Since("0.2.0")
public class ExprServerWhitelist extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprServerWhitelist.class, String.class)
						.supplier(ExprServerWhitelist::new)
						.addPatterns(
								"[(all [[of] the]|the)] whitelisted players of network server %string%")
						.build());
	}

	private Expression<String> server;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		server = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String name = server.getSingle(event);
		if (plugin == null || name == null)
			return new String[0];

		RemoteServer remote = plugin.network().server(name);
		return remote == null ? new String[0] : remote.whitelisted().toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "whitelisted players of network server " + server.toString(event, debug);
	}
}
