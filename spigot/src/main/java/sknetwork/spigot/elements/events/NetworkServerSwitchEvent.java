package sknetwork.spigot.elements.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import sknetwork.spigot.elements.types.NetworkPlayer;

public final class NetworkServerSwitchEvent extends NetworkPlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	public NetworkServerSwitchEvent(NetworkPlayer player, String from, String to) {
		super(player, from, to);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
