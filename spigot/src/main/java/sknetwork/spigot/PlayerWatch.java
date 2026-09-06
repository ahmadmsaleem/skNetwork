package sknetwork.spigot;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PlayerWatch implements Listener {

	private final SkNetworkSpigot plugin;
	private final AtomicBoolean queued = new AtomicBoolean();

	PlayerWatch(SkNetworkSpigot plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent event) {
		schedule();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		schedule();
	}

	private void schedule() {
		if (!queued.compareAndSet(false, true))
			return;

		plugin.getServer().getScheduler().runTask(plugin, () -> {
			queued.set(false);
			plugin.reportServerInfo();
		});
	}
}
