package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class PlayerPayloadTest {

	@Test
	void carriesATitleWithItsTimes() throws IOException {
		NetworkTitle sent = new NetworkTitle("{\"text\":\"hi\"}", "{\"text\":\"there\"}", 5, 40, 15);

		NetworkTitle read = NetworkTitle.read(roundTrip(sent::write));

		assertEquals(sent, read);
	}

	@Test
	void leavesOutASubtitleThatWasNeverSet() throws IOException {
		NetworkTitle read = NetworkTitle.read(roundTrip(
				new NetworkTitle("{\"text\":\"hi\"}", null, 1, 2, 3)::write));

		assertNull(read.subtitle());
		assertEquals("{\"text\":\"hi\"}", read.title());
	}

	@Test
	void keepsSoundVolumeAndPitchExactly() throws IOException {
		NetworkSound sent = new NetworkSound("minecraft:entity.player.levelup", 0.5f, 2.0f);

		NetworkSound read = NetworkSound.read(roundTrip(sent::write));

		assertEquals(sent, read);
		assertEquals(0.5f, read.volume());
	}

	@Test
	void carriesEitherHalfOfTheTabListOnItsOwn() throws IOException {
		NetworkTabList header = NetworkTabList.read(roundTrip(
				new NetworkTabList("{\"text\":\"top\"}", null)::write));
		NetworkTabList footer = NetworkTabList.read(roundTrip(
				new NetworkTabList(null, "{\"text\":\"bottom\"}")::write));

		assertEquals("{\"text\":\"top\"}", header.header());
		assertNull(header.footer());
		assertNull(footer.header());
		assertEquals("{\"text\":\"bottom\"}", footer.footer());
	}

	@Test
	void aBodyTravelsThroughTheRoutingPacketUntouched() throws IOException {
		byte[] body = PacketOut.body().string("carried").varInt(7).payload();

		Frame frame = new PacketOut(Protocol.PLAYER_ACTION)
				.varInt(PlayerAction.TITLE.id())
				.varInt(0)
				.nullableBytes(body)
				.frame();

		PacketIn read = frame.reader();
		assertEquals(PlayerAction.TITLE, PlayerAction.byId((byte) read.varInt()));
		assertEquals(0, read.varInt());

		PacketIn carried = new PacketIn(read.nullableBytes());
		assertEquals("carried", carried.string());
		assertEquals(7, carried.varInt());
	}

	private interface Writer {
		void write(PacketOut out);
	}

	private static PacketIn roundTrip(Writer writer) {
		PacketOut out = PacketOut.body();
		writer.write(out);
		return new PacketIn(out.payload());
	}
}
