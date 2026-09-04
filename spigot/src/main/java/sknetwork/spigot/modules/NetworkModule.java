package sknetwork.spigot.modules;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.util.SimpleEvent;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.elements.conditions.CondAtomicChange;
import sknetwork.spigot.elements.conditions.CondNetworkSynced;
import sknetwork.spigot.elements.effects.EffAtomic;
import sknetwork.spigot.elements.events.NetworkDisconnectEvent;
import sknetwork.spigot.elements.events.NetworkSyncEvent;
import sknetwork.spigot.elements.expressions.ExprAtomicError;
import sknetwork.spigot.elements.expressions.ExprAtomicResult;
import sknetwork.spigot.elements.expressions.ExprServerName;

/** Everything skNetwork adds to Skript's grammar. */
public final class NetworkModule implements AddonModule {


	@Override
	public @NotNull String name() {
		return "network";
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();

		CondNetworkSynced.register(registry);
		CondAtomicChange.register(registry);
		EffAtomic.register(registry);
		ExprServerName.register(registry);
		ExprAtomicResult.register(registry);
		ExprAtomicError.register(registry);

		registerEvents();
	}


	@SuppressWarnings("removal")
	private void registerEvents() {
		Skript.registerEvent("Network Sync", SimpleEvent.class, NetworkSyncEvent.class,
						"network sync[ed]")
				.description(
						"Fires on this server once its copy of the network variables is filled in and "
								+ "the proxy is accepting writes.",
						"It fires again after every reconnect, so use it to rebuild anything this "
								+ "server owns while it is running. A crash never fires `on quit`, so a "
								+ "cross-server player list keeps everyone who was online when the "
								+ "server died until something clears it.")
				.examples(
						"on network sync:",
						"\tdelete {?online::%network server name%::*}",
						"\tloop all players:",
						"\t\tadd loop-player's name to {?online::%network server name%::*}")
				.since("0.0.1");

		Skript.registerEvent("Network Disconnect", SimpleEvent.class, NetworkDisconnectEvent.class,
						"network disconnect[ed]")
				.description(
						"Fires on this server when it loses the proxy.",
						"Reads keep working from the copy this server already holds. Writes are "
								+ "refused until it reconnects.")
				.examples(
						"on network disconnect:",
						"\tbroadcast \"Lost the proxy. Balances are read only for now.\"")
				.since("0.0.1");
	}
}
