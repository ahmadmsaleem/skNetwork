package sknetwork.spigot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class NetworkText {

	public static String toJson(Component component) {
		return GsonComponentSerializer.gson().serialize(component);
	}

	public static Component fromJson(String json) {
		return GsonComponentSerializer.gson().deserialize(json);
	}

	private NetworkText() {
	}
}
