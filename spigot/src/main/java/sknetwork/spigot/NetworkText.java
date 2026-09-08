package sknetwork.spigot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class NetworkText {

	public static String toJson(Component component) {
		return GsonComponentSerializer.gson().serialize(component);
	}

	private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
			.character('\u00a7').hexColors().build();

	/** How a component reads as a plain Skript string. */
	public static String toLegacy(Component component) {
		return SECTION.serialize(component);
	}

	public static Component fromJson(String json) {
		return GsonComponentSerializer.gson().deserialize(json);
	}

	private NetworkText() {
	}
}
