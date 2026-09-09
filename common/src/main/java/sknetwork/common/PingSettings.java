package sknetwork.common;

import java.io.IOException;

public record PingSettings(String motd, String maxPlayers, String playerCount) {

	public static final PingSettings NONE = new PingSettings(null, null, null);

	public String get(PingField field) {
		return switch (field) {
			case MOTD -> motd;
			case MAX_PLAYERS -> maxPlayers;
			case PLAYER_COUNT -> playerCount;
		};
	}

	public PingSettings with(PingField field, String value) {
		return switch (field) {
			case MOTD -> new PingSettings(value, maxPlayers, playerCount);
			case MAX_PLAYERS -> new PingSettings(motd, value, playerCount);
			case PLAYER_COUNT -> new PingSettings(motd, maxPlayers, value);
		};
	}

	public boolean isEmpty() {
		return motd == null && maxPlayers == null && playerCount == null;
	}

	/** @return the field as a whole number, or null when it is unset or not a number */
	public Integer number(PingField field) {
		String value = get(field);
		if (value == null)
			return null;
		try {
			return Integer.valueOf(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public void write(PacketOut out) {
		out.nullableString(motd).nullableString(maxPlayers).nullableString(playerCount);
	}

	public static PingSettings read(PacketIn in) throws IOException {
		return new PingSettings(in.nullableString(), in.nullableString(), in.nullableString());
	}
}
