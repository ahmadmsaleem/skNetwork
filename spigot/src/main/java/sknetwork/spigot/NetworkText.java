package sknetwork.spigot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Chat components travel as the JSON Minecraft already speaks, so the proxy can
 * carry one without knowing anything about Adventure and every backend renders it
 * with its own Paper.
 */
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
