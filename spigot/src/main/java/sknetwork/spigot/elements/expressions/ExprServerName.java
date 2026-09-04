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
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Server Name")
@Description({
		"The name this server goes by on the network.",
		"It is the `server-name` value from this server's skNetwork config, or `server-<port>` when "
				+ "that is left blank.",
		"One script can be pushed to every server at once, so this is how a single file can behave "
				+ "differently depending on where it landed."
})
@Example("""
		on join:
			add player's name to {?online::%network server name%::*}

		on quit:
			remove player's name from {?online::%network server name%::*}
		""")
@Since("0.0.1")
public class ExprServerName extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprServerName.class, String.class)
						.supplier(ExprServerName::new)
						.addPatterns(
								"network server name",
								"name of th[e|is] network server")
						.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		return plugin == null ? new String[0] : new String[] { plugin.serverName() };
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
		return "network server name";
	}
}
