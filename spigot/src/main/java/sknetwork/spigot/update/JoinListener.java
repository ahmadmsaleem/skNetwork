package sknetwork.spigot.update;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class JoinListener implements Listener {

	private static final long SETTLE_TICKS = 30L;

	private final JavaPlugin plugin;

	JoinListener(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	private void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (!player.hasPermission("sknetwork.updates.view"))
			return;

		plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			GitHubRelease newer = UpdateChecker.available();
			if (newer == null || !player.isOnline())
				return;

			UpdateChecker.notice(plugin, newer).forEach(player::sendMessage);
		}, SETTLE_TICKS);
	}
}
