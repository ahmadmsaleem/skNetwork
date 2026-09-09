package sknetwork.spigot.elements.effects;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import sknetwork.common.PacketOut;
import sknetwork.spigot.elements.types.NetworkPlayer;
import sknetwork.spigot.elements.types.NetworkPlayers;

public final class NetworkTargets {

	public record Targets(boolean everyone, List<String> names) {
	}

	public static final Targets EVERYONE = new Targets(true, List.of());

	public static Targets of(Expression<NetworkPlayer> targets, Event event) {
		if (targets == null)
			return EVERYONE;

		List<String> names = new ArrayList<>();
		for (NetworkPlayer player : targets.getArray(event)) {
			String name = NetworkPlayers.name(player);
			if (name != null)
				names.add(name);
		}
		return new Targets(false, names);
	}

	public static byte[] text(String value) {
		return PacketOut.body().string(value).payload();
	}

	private NetworkTargets() {
	}
}
