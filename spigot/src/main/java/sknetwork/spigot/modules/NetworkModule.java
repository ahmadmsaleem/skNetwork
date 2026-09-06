package sknetwork.spigot.modules;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.util.SimpleEvent;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.elements.conditions.CondAtomicChange;
import sknetwork.spigot.elements.conditions.CondNetworkSynced;
import sknetwork.spigot.elements.conditions.CondServerOnline;
import sknetwork.spigot.elements.effects.EffAtomic;
import sknetwork.spigot.elements.effects.EffConnectPlayer;
import sknetwork.spigot.elements.effects.EffNetworkActionBar;
import sknetwork.spigot.elements.effects.EffNetworkMessage;
import sknetwork.spigot.elements.effects.EffServerCommand;
import sknetwork.spigot.elements.events.NetworkDisconnectEvent;
import sknetwork.spigot.elements.events.EvtNetworkVariable;
import sknetwork.spigot.elements.events.NetworkSyncEvent;
import sknetwork.spigot.elements.events.NetworkVariableChangeEvent;
import sknetwork.spigot.elements.expressions.ExprAtomicError;
import sknetwork.spigot.elements.expressions.ExprAtomicResult;
import sknetwork.spigot.elements.expressions.ExprChangedVariable;
import sknetwork.spigot.elements.expressions.ExprNetworkPlayers;
import sknetwork.spigot.elements.expressions.ExprNetworkServers;
import sknetwork.spigot.elements.expressions.ExprPlayerServer;
import sknetwork.spigot.elements.expressions.ExprServerDetail;
import sknetwork.spigot.elements.expressions.ExprServerMaxPlayers;
import sknetwork.spigot.elements.expressions.ExprServerName;
import sknetwork.spigot.elements.expressions.ExprServerWhitelist;

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

		CondServerOnline.register(registry);
		EffNetworkMessage.register(registry);
		EffNetworkActionBar.register(registry);
		EffConnectPlayer.register(registry);
		EffServerCommand.register(registry);
		ExprNetworkServers.register(registry);
		ExprNetworkPlayers.register(registry);
		ExprPlayerServer.register(registry);
		ExprServerDetail.register(registry);
		ExprServerMaxPlayers.register(registry);
		ExprServerWhitelist.register(registry);
		ExprChangedVariable.register(registry);

		registerEvents();
	}


	@SuppressWarnings("removal")
	private void registerEvents() {
		Skript.registerEvent("Network Sync", SimpleEvent.class, NetworkSyncEvent.class,
						"network sync[ed]")
				.description("""
						Fires on this server once its copy of the network variables is filled in and the proxy is accepting writes.
						It fires again after every reconnect, so use it to rebuild anything this server owns while it is running. A crash never fires `on quit`, so a cross-server player list keeps everyone who was online when the server died until something clears it.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network sync:
							delete {?online::%network server name%::*}
							loop all players:
								add loop-player's name to {?online::%network server name%::*}
						""")
				.since("0.0.1");

		Skript.registerEvent("Network Disconnect", SimpleEvent.class, NetworkDisconnectEvent.class,
						"network disconnect[ed]")
				.description("""
						Fires on this server when it loses the proxy.
						Reads keep working from the copy this server already holds. Writes are refused until it reconnects.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network disconnect:
							broadcast "Lost the proxy. Balances are read only for now."
						""")
				.since("0.0.1");

		Skript.registerEvent("Network Variable Change", EvtNetworkVariable.class,
						NetworkVariableChangeEvent.class,
						"network variable change [of %-string%]")
				.description("""
						Fires on every server the moment a network variable changes, including the one that wrote it.
						Give it a name to listen to one branch instead of every write on the network. A `*` is a wildcard, the same as `/sknetproxy dump`.
						That name is the stored key, not a variable name, so a `%player%` written inside it is never filled in. Match the branch with `*` and read `the changed variable` to find out who it was.
						Nothing fires while a snapshot is arriving, so a server joining does not replay the whole map as changes.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network variable change of "inbox::*":
							set {_uuid} to the second element of the changed variable split at "::"
							if player with uuid {_uuid} is online:
								send the new value to player with uuid {_uuid}
						""")
				.since("0.2.0");
	}
}
