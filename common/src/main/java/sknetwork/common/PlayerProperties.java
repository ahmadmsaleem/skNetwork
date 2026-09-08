package sknetwork.common;

import java.io.IOException;
import java.util.Objects;

public record PlayerProperties(String player, int ping, String ip, String displayName,
		String locale, long playtimeTicks) {

	/** Ping jitter under this is not worth a packet. */
	public static final int PING_STEP = 5;

	private static final long TICKS_PER_MS = 50;

	/**
	 * @return true when this is worth sending again, so a ping wobbling by a
	 *         millisecond does not put every player back on the wire
	 */
	public boolean differsFrom(PlayerProperties sent) {
		if (sent == null)
			return true;
		return Math.abs(ping - sent.ping()) >= PING_STEP
				|| !Objects.equals(ip, sent.ip())
				|| !Objects.equals(displayName, sent.displayName())
				|| !Objects.equals(locale, sent.locale());
	}

	/**
	 * Playtime only ever counts up while somebody is online, so it travels once as a
	 * baseline and every reader adds the time since it arrived.
	 */
	public PlayerProperties advancedBy(long millis) {
		return millis <= 0 ? this : new PlayerProperties(player, ping, ip, displayName, locale,
				playtimeTicks + millis / TICKS_PER_MS);
	}

	public void write(PacketOut out) {
		out.string(player).varInt(Math.max(ping, 0))
				.nullableString(ip).nullableString(displayName).nullableString(locale)
				.int64(playtimeTicks);
	}

	public static PlayerProperties read(PacketIn in) throws IOException {
		return new PlayerProperties(in.string(), in.varInt(), in.nullableString(),
				in.nullableString(), in.nullableString(), in.int64());
	}
}
