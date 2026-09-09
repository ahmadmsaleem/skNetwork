package sknetwork.spigot.elements.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import sknetwork.spigot.elements.types.NetworkPlayer;

public final class NetworkPlayerJoinEvent extends NetworkPlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	public NetworkPlayerJoinEvent(NetworkPlayer player, String to) {
		super(player, null, to);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
