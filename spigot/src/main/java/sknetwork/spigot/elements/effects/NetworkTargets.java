package sknetwork.spigot.elements.effects;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import sknetwork.common.PacketOut;
import sknetwork.spigot.elements.types.NetworkPlayer;

public final class NetworkTargets {

	public static List<String> names(Expression<NetworkPlayer> targets, Event event) {
		if (targets == null)
			return List.of();

		List<String> names = new ArrayList<>();
		for (NetworkPlayer player : targets.getArray(event))
			if (player.name() != null)
				names.add(player.name());
		return names;
	}

	public static byte[] text(String value) {
		return PacketOut.body().string(value).payload();
	}

	private NetworkTargets() {
	}
}
