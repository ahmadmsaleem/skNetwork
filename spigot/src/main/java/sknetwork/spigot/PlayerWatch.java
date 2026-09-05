package sknetwork.spigot;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the proxy's picture of who is here up to date. Reports on the tick after
 * the event, because a quitting player is still online during their own quit, and
 * only once per tick, because ten players joining at once is one change worth
 * sending, not ten.
 */
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
