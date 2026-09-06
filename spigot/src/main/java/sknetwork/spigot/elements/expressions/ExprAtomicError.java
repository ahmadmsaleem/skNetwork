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

@Name("Atomic Error")
@Description("""
		Why the proxy refused the last `atomically ... and wait`, or why no reply came back.
		A refusal reads like `already set`, `current value does not match`, or a message naming the variable that would drop below its floor. Those all mean the change did not happen.
		A timeout instead says that whether it applied is unknown. Use `the atomic change timed out` to tell the two apart rather than reading this text.
		Empty when the change was taken.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Atomic-Changes
		""")
@Example("""
		atomically set {?rank::%player%} to "vip" if it is "default" and wait
		if the atomic change was refused:
			send "Could not promote: %the atomic error%"
		""")
@Since("0.0.1")
public class ExprAtomicError extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
				DefaultSyntaxInfos.Expression.builder(ExprAtomicError.class, String.class)
						.supplier(ExprAtomicError::new)
						.addPatterns("[the] atomic (error|refusal reason)")
						.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		AtomicResult result = LastAtomic.of(event);
		String error = result == null ? null : result.error();
		return error == null ? new String[0] : new String[] { error };
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
		return "the atomic error";
	}
}
