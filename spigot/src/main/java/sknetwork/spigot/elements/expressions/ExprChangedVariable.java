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
import sknetwork.spigot.elements.events.NetworkVariableChangeEvent;

@Name("Changed Network Variable")
@Description("""
		Inside `on network variable change`, the name that changed and the values either side of the change.
		The name carries no prefix, because that is how it travels between servers. A delete leaves the new value unset.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
		""")
@Example("""
		on network variable change of "coins::*":
			broadcast "%the changed variable% went from %the old value% to %the new value%"
		""")
@Since("0.2.0")
public class ExprChangedVariable extends SimpleExpression<Object> {

	private static final int NAME = 0;
	private static final int NEW = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprChangedVariable.class, Object.class)
						.supplier(ExprChangedVariable::new)
						.addPatterns(
								"[the] changed [network] variable",
								"[the] new value",
								"[the] (old|previous) value")
						.build());
	}

	private int part;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		part = matchedPattern;
		return getParser().isCurrentEvent(NetworkVariableChangeEvent.class);
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		if (!(event instanceof NetworkVariableChangeEvent change))
			return new Object[0];

		Object value = switch (part) {
			case NAME -> change.variable();
			case NEW -> change.newValue();
			default -> change.oldValue();
		};
		return value == null ? new Object[0] : new Object[] { value };
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return part == NAME ? String.class : Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return switch (part) {
			case NAME -> "the changed variable";
			case NEW -> "the new value";
			default -> "the old value";
		};
	}
}
