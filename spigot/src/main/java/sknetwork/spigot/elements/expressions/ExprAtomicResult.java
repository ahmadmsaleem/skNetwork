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
import sknetwork.spigot.elements.types.AtomicResult;
import sknetwork.spigot.elements.types.LastAtomic;

@Name("Atomic Result")
@Description({
		"The value the proxy holds after it took the last `atomically ... and wait`. For an add, "
				+ "that is the new total.",
		"The proxy worked this number out itself, so it is right even when the change has not "
				+ "reached this server's own copy yet.",
		"Empty when the proxy refused the change, and empty when no reply came back."
})
@Example("""
		atomically add 50 to {?coins::%uuid of player%} and wait
		if the atomic change succeeded:
			send "You now have %the atomic result% coins."
		""")
@Since("0.0.1")
public class ExprAtomicResult extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprAtomicResult.class, Object.class)
						.supplier(ExprAtomicResult::new)
						.addPatterns("[the] atomic result")
						.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		return true;
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		AtomicResult result = LastAtomic.of(event);
		Object value = result == null ? null : result.value();
		return value == null ? new Object[0] : new Object[] { value };
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "the atomic result";
	}
}
