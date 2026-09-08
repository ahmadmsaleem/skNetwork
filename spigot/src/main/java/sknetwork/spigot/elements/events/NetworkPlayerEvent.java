package sknetwork.spigot.elements.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import sknetwork.spigot.elements.types.NetworkPlayer;

public abstract class NetworkPlayerEvent extends Event {

	private final NetworkPlayer player;
	private final String from;
	private final String to;

	protected NetworkPlayerEvent(NetworkPlayer player, @Nullable String from, @Nullable String to) {
		super(!Bukkit.isPrimaryThread());
		this.player = player;
		this.from = from;
		this.to = to;
	}

	public NetworkPlayer networkPlayer() {
		return player;
	}

	public @Nullable String previousServer() {
		return from;
	}

	public @Nullable String newServer() {
		return to;
	}
}
