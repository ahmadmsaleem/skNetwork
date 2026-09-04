package sknetwork.spigot.elements.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.skript.variables.SerializedVariable;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.common.MutationMode;
import sknetwork.spigot.SkNetworkSpigot;
import sknetwork.spigot.elements.types.AtomicChange;
import sknetwork.spigot.elements.types.LastAtomic;

@Name("Atomically Change A Network Variable")
@Description({
		"Changes a network variable on the proxy instead of on this server.",
		"Two servers running a plain `add` at the same moment both read the same number, so one of "
				+ "the two adds is lost. The proxy does the sum on the one thread that owns the data, "
				+ "so that cannot happen.",
		"A plain `remove` is only subtraction and will go past zero without complaining. Use "
				+ "`without going below` whenever you are spending money.",
		"`set ... if it is not set` is how you hand out a starting balance once for the whole "
				+ "network, no matter which server sees the player first.",
		"Add `and wait` to hold the trigger until the proxy replies, then read the outcome with "
				+ "`the atomic change succeeded` and `the atomic result`. Everything after it runs on "
				+ "a later tick, so the usual Skript delay rules apply: you cannot cancel the event "
				+ "past that point, and it does not belong in a function."
})
@Example("""
		atomically add 50 to {?coins::%uuid of player%}
		atomically remove 50 from {?coins::%uuid of player%}
		atomically set {?coins::%uuid of player%} to 100 if it is not set
		atomically set {?rank::%uuid of player%} to "vip" if it is "default"
		""")
@Example("""
		# spending money safely, on any server
		atomically remove 250 from {?coins::%uuid of player%} without going below 0 and wait
		if the atomic change succeeded:
			give player a diamond
			send "You have %the atomic result% coins left."
		else:
			send "Not enough: %the atomic error%"
		""")
@Since("0.0.1")
public class EffAtomic extends Effect {


	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffAtomic.class)
				.supplier(EffAtomic::new)
				.addPatterns(
						"atomically add %number% to %objects% [wait:and wait]",
						"atomically (remove|subtract) %number% from %objects% [wait:and wait]",
						"atomically set %objects% to %object% if it( is|'s) not set [wait:and wait]",
						"atomically set %objects% to %object% if it( is|'s) %object% [wait:and wait]",
						// the money one: a plain remove has no floor and will happily go negative
						"atomically (remove|subtract|take) %number% from %objects% "
								+ "without going below %number% [wait:and wait]")
				.build());
	}

	private Variable<?> variable;
	private Expression<?> value;
	private Expression<?> expected;
	private MutationMode mode;
	private boolean waiting;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		Expression<?> target = switch (matchedPattern) {
			case 0, 1, 4 -> exprs[1];
			default -> exprs[0];
		};

		if (!(target instanceof Variable<?> raw)) {
			Skript.error("Only a variable can be changed atomically, and it has to be a network one.");
			return false;
		}
		if (raw.isLocal()) {
			Skript.error("A local variable never leaves this server, so there is nothing to make atomic.");
			return false;
		}
		if (raw.isList()) {
			Skript.error("Atomic changes work on a single variable, not a list.");
			return false;
		}

		variable = raw;
		// a literal arrives as UnparsedLiteral and throws on getSingle unless converted
		// at parse time. this is Skript's supported way.
		value = LiteralUtils.defendExpression(matchedPattern <= 1 || matchedPattern == 4
				? exprs[0]
				: exprs[1]);
		// pattern 3 carries the value to compare against, pattern 4 the floor. both ride
		// the same two wire fields, because to the proxy both are "the condition".
		expected = matchedPattern == 3 || matchedPattern == 4
				? LiteralUtils.defendExpression(exprs[2])
				: null;
		if (!LiteralUtils.canInitSafely(expected == null
				? new Expression<?>[] { value }
				: new Expression<?>[] { value, expected }))
			return false;

		mode = switch (matchedPattern) {
			case 0 -> MutationMode.ADD;
			case 1 -> MutationMode.REMOVE;
			case 2 -> MutationMode.SET_IF_ABSENT;
			case 3 -> MutationMode.COMPARE_AND_SET;
			default -> MutationMode.REMOVE_IF_ABOVE;
		};

		waiting = result.hasTag("wait");
		// everything after this runs on a later tick, and Skript has to know that before
		// it parses the next line
		if (waiting)
			getParser().setHasDelayBefore(Kleenean.TRUE);
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin == null)
			return;

		AtomicChange change = build(event, plugin);
		if (change != null)
			plugin.atomic(change);
	}


	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		if (!waiting)
			return super.walk(event);

		debug(event, true);
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		TriggerItem next = getNext();
		AtomicChange change = plugin == null ? null : build(event, plugin);

		if (change == null || next == null) {
			if (change != null)
				plugin.atomic(change);
			return next;
		}

		Object locals = Variables.removeLocals(event);

		// called exactly once, always on the main thread. a refusal, a timeout and a lost
		// proxy all arrive here, so the trigger cannot be stranded.
		plugin.atomic(change, result -> {
			// Skript's own Delay refuses to resume during shutdown, for the same reason:
			// this would run script code against half-disabled plugins
			if (!Skript.getInstance().isEnabled())
				return;

			Delay.addDelayedEvent(event);
			LastAtomic.remember(event, result);
			if (locals != null)
				Variables.setLocalVariables(event, locals);

			TriggerItem.walk(next, event);
			Variables.removeLocals(event);
		});
		return null;
	}

	/** @return null when the value cannot be serialised, which is already logged */
	private @Nullable AtomicChange build(Event event, SkNetworkSpigot plugin) {
		String local = variable.getName().toString(event);

		Object single = value.getSingle(event);
		SerializedVariable.Value amount = single == null ? null : Variables.serialize(single);
		if (amount == null) {
			plugin.getLogger().warning("cannot send {" + local + "} to the proxy: the value is empty"
					+ " or of a type Skript cannot serialise");
			return null;
		}

		Object compareTo = expected == null ? null : expected.getSingle(event);
		SerializedVariable.Value compare = compareTo == null ? null : Variables.serialize(compareTo);

		return new AtomicChange(mode, local, amount.type, amount.data,
				compare == null ? null : compare.type,
				compare == null ? null : compare.data,
				plugin.describe(single));
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "atomically " + mode + " on " + variable.toString(event, debug)
				+ (waiting ? " and wait" : "");
	}
}
