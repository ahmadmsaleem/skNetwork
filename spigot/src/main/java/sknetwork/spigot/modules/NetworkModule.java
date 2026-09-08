package sknetwork.spigot.modules;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.registrations.EventValues;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.spigot.elements.conditions.CondAtomicChange;
import sknetwork.spigot.elements.conditions.CondNetworkSynced;
import sknetwork.spigot.elements.conditions.CondNetworkPlayerOnline;
import sknetwork.spigot.elements.conditions.CondServerOnline;
import sknetwork.spigot.elements.effects.EffAtomic;
import sknetwork.spigot.elements.effects.EffConnectPlayer;
import sknetwork.spigot.elements.effects.EffNetworkActionBar;
import sknetwork.spigot.elements.effects.EffNetworkMessage;
import sknetwork.spigot.elements.effects.EffNetworkSound;
import sknetwork.spigot.elements.effects.EffNetworkTabList;
import sknetwork.spigot.elements.effects.EffNetworkTitle;
import sknetwork.spigot.elements.effects.EffServerCommand;
import sknetwork.spigot.elements.events.NetworkDisconnectEvent;
import sknetwork.spigot.elements.events.NetworkPlayerEvent;
import sknetwork.spigot.elements.events.NetworkPlayerJoinEvent;
import sknetwork.spigot.elements.events.NetworkPlayerQuitEvent;
import sknetwork.spigot.elements.events.NetworkServerSwitchEvent;
import sknetwork.spigot.elements.events.EvtNetworkVariable;
import sknetwork.spigot.elements.events.NetworkSyncEvent;
import sknetwork.spigot.elements.events.NetworkVariableChangeEvent;
import sknetwork.spigot.elements.expressions.ExprAtomicError;
import sknetwork.spigot.elements.expressions.ExprAtomicResult;
import sknetwork.spigot.elements.expressions.ExprChangedVariable;
import sknetwork.spigot.elements.expressions.ExprEventNetworkServer;
import sknetwork.spigot.elements.expressions.ExprNetworkPlayer;
import sknetwork.spigot.elements.expressions.ExprNetworkPing;
import sknetwork.spigot.elements.expressions.ExprNetworkPlayerDetail;
import sknetwork.spigot.elements.expressions.ExprNetworkPlayers;
import sknetwork.spigot.elements.expressions.ExprNetworkServers;
import sknetwork.spigot.elements.expressions.ExprPlayerServer;
import sknetwork.spigot.elements.expressions.ExprServerDetail;
import sknetwork.spigot.elements.expressions.ExprServerMaxPlayers;
import sknetwork.spigot.elements.expressions.ExprServerName;
import sknetwork.spigot.elements.expressions.ExprServerWhitelist;
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.NetworkPlayerType;

/** Everything skNetwork adds to Skript's grammar. */
public final class NetworkModule implements AddonModule {


	@Override
	public @NotNull String name() {
		return "network";
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		NetworkPlayerType.register();

		SyntaxRegistry registry = addon.syntaxRegistry();

		CondNetworkSynced.register(registry);
		CondAtomicChange.register(registry);
		EffAtomic.register(registry);
		ExprServerName.register(registry);
		ExprAtomicResult.register(registry);
		ExprAtomicError.register(registry);

		CondServerOnline.register(registry);
		CondNetworkPlayerOnline.register(registry);
		EffNetworkMessage.register(registry);
		EffNetworkActionBar.register(registry);
		EffNetworkTitle.register(registry);
		EffNetworkSound.register(registry);
		EffNetworkTabList.register(registry);
		EffConnectPlayer.register(registry);
		EffServerCommand.register(registry);
		ExprNetworkServers.register(registry);
		ExprNetworkPlayer.register(registry);
		ExprNetworkPlayers.register(registry);
		ExprNetworkPlayerDetail.register(registry);
		ExprPlayerServer.register(registry);
		ExprServerDetail.register(registry);
		ExprServerMaxPlayers.register(registry);
		ExprServerWhitelist.register(registry);
		ExprChangedVariable.register(registry);
		ExprEventNetworkServer.register(registry);
		ExprNetworkPing.register(registry);

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
							set {_who} to the last element of (the changed variable split at "::")
							set {_player} to {_who} parsed as offline player
							if {_player} is online:
								send the new value to {_player}
						""")
				.since("0.2.0");

		Skript.registerEvent("Network Player Join", SimpleEvent.class, NetworkPlayerJoinEvent.class,
						"network player join[ed]")
				.description("""
						Fires when somebody joins the network. It fires on every server, not only the one they landed on.
						That server also runs its own `on join`, so both fire there. Compare `the new network server` with `network server name` when only the server holding them should act.
						Somebody moving between two servers is a switch, not a quit followed by a join.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network player join:
							broadcast "%event-networkplayer% joined %the new network server%"
						""")
				.since("1.0.0");

		Skript.registerEvent("Network Player Quit", SimpleEvent.class, NetworkPlayerQuitEvent.class,
						"network player (quit|leave|disconnect)[ed]")
				.description("""
						Fires when somebody leaves the network altogether. It fires on every server.
						Moving between two servers does not fire this. The proxy holds a quit back for a moment and turns it into a switch if they turn up on another server, so this only fires once they are really gone.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network player quit:
							delete {?online::%event-networkplayer%}
						""")
				.since("1.0.0");

		Skript.registerEvent("Network Server Switch", SimpleEvent.class, NetworkServerSwitchEvent.class,
						"network server switch[ed]")
				.description("""
						Fires when somebody moves from one server to another. It fires on every server, including the two they moved between.
						Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Sync-and-Events
						""")
				.examples("""
						on network server switch:
							broadcast "%event-networkplayer%: %the previous network server% -> %the new network server%"
						""")
				.since("1.0.0");

		EventValues.registerEventValue(NetworkPlayerJoinEvent.class, NetworkPlayer.class,
				NetworkPlayerEvent::networkPlayer);
		EventValues.registerEventValue(NetworkPlayerQuitEvent.class, NetworkPlayer.class,
				NetworkPlayerEvent::networkPlayer);
		EventValues.registerEventValue(NetworkServerSwitchEvent.class, NetworkPlayer.class,
				NetworkPlayerEvent::networkPlayer);
	}
}
