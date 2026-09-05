package sknetwork.spigot.elements.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on every server the moment a network variable changes, including the one
 * that wrote it. The name carries no prefix, the way it travels on the wire.
 */
public class NetworkVariableChangeEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final String name;
	private final Object newValue;
	private final Object oldValue;

	public NetworkVariableChangeEvent(String name, @Nullable Object newValue, @Nullable Object oldValue) {
		super(!org.bukkit.Bukkit.isPrimaryThread());
		this.name = name;
		this.newValue = newValue;
		this.oldValue = oldValue;
	}

	public String variable() {
		return name;
	}

	public @Nullable Object newValue() {
		return newValue;
	}

	public @Nullable Object oldValue() {
		return oldValue;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
