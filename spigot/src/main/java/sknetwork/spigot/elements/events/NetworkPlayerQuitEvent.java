package sknetwork.spigot.elements.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import sknetwork.spigot.elements.types.NetworkPlayer;

public final class NetworkPlayerQuitEvent extends NetworkPlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	public NetworkPlayerQuitEvent(NetworkPlayer player, String from) {
		super(player, from, null);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
