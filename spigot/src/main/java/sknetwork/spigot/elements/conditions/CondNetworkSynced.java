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
import sknetwork.spigot.SkNetworkSpigot;

@Name("Network Is Synced")
@Description("""
		Checks whether this server has finished its first sync with the proxy.
		Until it has, the local copy of the network variables is empty. Every `is not set` check says yes, even for values that other servers can see, and writes are refused.
		Guard anything that reads or writes a network variable on join with this.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
		""")
@Example("""
		on join:
			if network is not synced:
				kick player due to "The network is still starting up. Try again in a moment."
				stop
		""")
@Since("0.0.1")
public class CondNetworkSynced extends Condition {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondNetworkSynced.class)
				.supplier(CondNetworkSynced::new)
				.addPatterns(
						"network is synced",
						"network (is not|isn't) synced")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		return isNegated() != (plugin != null && plugin.isSynced());
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network is " + (isNegated() ? "not " : "") + "synced";
	}
}
