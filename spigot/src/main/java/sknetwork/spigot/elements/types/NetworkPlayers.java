package sknetwork.spigot.elements.types;

import java.util.UUID;

import ch.njol.skript.SkriptConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import sknetwork.spigot.SkNetworkSpigot;

public final class NetworkPlayers {

	public static String name(NetworkPlayer player) {
		if (player.name() != null)
			return player.name();

		Player online = Bukkit.getPlayer(player.uuid());
		return online != null ? online.getName() : Bukkit.getOfflinePlayer(player.uuid()).getName();
	}

	public static UUID uuid(NetworkPlayer player) {
		if (player.uuid() != null)
			return player.uuid();

		Player online = Bukkit.getPlayerExact(player.name());
		if (online != null)
			return online.getUniqueId();

		OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(player.name());
		return cached == null ? null : cached.getUniqueId();
	}

	public static String variableName(NetworkPlayer player) {
		if (!SkriptConfig.usePlayerUUIDsInVariableNames.value())
			return player.display();

		UUID uuid = uuid(player);
		if (uuid != null)
			return uuid.toString();

		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		if (plugin != null)
			plugin.warnUnresolvedPlayerKey(player.display());
		return player.display();
	}

	private NetworkPlayers() {
	}
}
