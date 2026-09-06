package sknetwork.spigot.elements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.elements.types.AtomicResult;
import sknetwork.spigot.elements.types.LastAtomic;

@Name("Atomic Change Outcome")
@Description("""
		Checks what the proxy did with the last `atomically ... and wait` in this trigger.
		There are three answers, not two. The proxy took the change, the proxy refused it, or the proxy never replied.
		Only a refusal proves the change did not happen. When no reply comes back, the proxy may have applied the change and lost the answer, so treat that as unknown.
		`failed` covers a refusal and a timeout together. Use it only where you would handle both the same way.
		A trigger that never waited has no answer, so all of these are false.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Atomic-Changes
		""")
@Example("""
		atomically remove 100 from {?coins::%player%} without going below 0 and wait
		if the atomic change succeeded:
			send "Bought. You have %the atomic result% left."
		else if the atomic change timed out:
			send "The network is slow right now. Try again in a moment."
		else:
			send "Not enough: %the atomic error%"
		""")
@Since("0.0.1")
public class CondAtomicChange extends Condition {

	public static void register(@NotNull SyntaxRegistry registry) {
		// 'failed' and 'timed out' overlap on purpose: a timeout is a failure, but only a
		// refusal proves the change did not happen
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondAtomicChange.class)
				.supplier(CondAtomicChange::new)
				.addPatterns(
						"[the] atomic change (succeeded|was accepted)",
						"[the] atomic change (failed|was refused)",
						"[the] atomic change (timed out|went unanswered)",
						"[the] atomic change (was answered|did not time out)")
				.build());
	}

	private boolean checksAnswer;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		checksAnswer = matchedPattern >= 2;
		setNegated(matchedPattern == 1 || matchedPattern == 3);
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		AtomicResult result = LastAtomic.of(event);
		boolean value = checksAnswer
				? result != null && !result.answered()
				: result != null && result.ok();
		return isNegated() != value;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		if (checksAnswer)
			return "the atomic change " + (isNegated() ? "was answered" : "timed out");
		return "the atomic change " + (isNegated() ? "was refused" : "succeeded");
	}
}
