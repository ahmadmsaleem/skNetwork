package sknetwork.spigot.elements.types;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class NetworkPlayer {

	private static final Pattern PLAUSIBLE_NAME = Pattern.compile("[A-Za-z0-9_.*]{1,32}");

	private final String name;
	private final UUID uuid;

	private NetworkPlayer(String name, UUID uuid) {
		this.name = name;
		this.uuid = uuid;
	}

	public static NetworkPlayer named(String name) {
		return name == null || name.isBlank() ? null : new NetworkPlayer(name.trim(), null);
	}

	public static NetworkPlayer known(String name, UUID uuid) {
		if (uuid == null)
			return named(name);
		return new NetworkPlayer(name == null || name.isBlank() ? null : name.trim(), uuid);
	}

	public static NetworkPlayer parse(String input) {
		if (input == null)
			return null;

		String trimmed = input.trim();
		UUID parsed = readUuid(trimmed);
		if (parsed != null)
			return new NetworkPlayer(null, parsed);
		return PLAUSIBLE_NAME.matcher(trimmed).matches() ? new NetworkPlayer(trimmed, null) : null;
	}

	private static UUID readUuid(String input) {
		if (input.length() != 36)
			return null;
		try {
			return UUID.fromString(input);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	public String display() {
		return name != null ? name : uuid.toString();
	}

	private String key() {
		return name != null ? name.toLowerCase(Locale.ROOT) : uuid.toString();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NetworkPlayer player && key().equals(player.key());
	}

	@Override
	public int hashCode() {
		return key().hashCode();
	}

	@Override
	public String toString() {
		return display();
	}
}
