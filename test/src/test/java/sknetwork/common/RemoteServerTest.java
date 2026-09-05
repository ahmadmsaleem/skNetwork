package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RemoteServerTest {

	@Test
	void survivesARoundTrip() throws IOException {
		RemoteServer original = new RemoteServer("lobby", "A Lobby", "26.1.2", 100,
				List.of("eult", "njol"), List.of("eult"));

		assertEquals(original, roundTrip(original));
	}

	@Test
	void carriesAnEmptyServer() throws IOException {
		RemoteServer read = roundTrip(RemoteServer.empty("lobby"));

		assertEquals("lobby", read.name());
		assertTrue(read.players().isEmpty());
		assertTrue(read.whitelisted().isEmpty());
	}

	@Test
	void keepsPlayersAndWhitelistApart() throws IOException {
		RemoteServer read = roundTrip(new RemoteServer("s", "m", "v", 20,
				List.of("a", "b", "c"), List.of("d")));

		assertEquals(List.of("a", "b", "c"), read.players());
		assertEquals(List.of("d"), read.whitelisted());
	}

	@Test
	void keepsAwkwardNames() throws IOException {
		RemoteServer read = roundTrip(new RemoteServer("s", "§aMOTD\nline two", "v", 1,
				List.of("café"), List.of()));

		assertEquals("§aMOTD\nline two", read.motd());
		assertEquals("café", read.players().get(0));
	}

	@Test
	void refusesAPlayerCountOutOfRange() throws IOException {
		PacketOut out = new PacketOut(Protocol.SERVER_INFO)
				.string("s").string("m").string("v").varInt(1).varInt(200_000);

		IOException thrown = assertThrows(IOException.class,
				() -> RemoteServer.read(out.frame().reader()));
		assertTrue(thrown.getMessage().contains("out of range"));
	}

	@ParameterizedTest
	@EnumSource(PlayerAction.class)
	void everyActionSurvivesItsId(PlayerAction action) {
		assertEquals(action, PlayerAction.byId(action.id()));
	}

	@Test
	void pinsTheActionWireOrder() {
		assertEquals(0, PlayerAction.MESSAGE.ordinal());
		assertEquals(1, PlayerAction.ACTION_BAR.ordinal());
		assertEquals(2, PlayerAction.CONNECT.ordinal());
		assertEquals(3, PlayerAction.values().length);
	}

	@Test
	void onlyConnectNeedsTheProxy() {
		assertTrue(PlayerAction.CONNECT.handledByProxy());
		assertTrue(PlayerAction.MESSAGE.handledByProxy() == false);
	}

	@Test
	void refusesAnActionItDoesNotKnow() {
		assertThrows(IllegalArgumentException.class, () -> PlayerAction.byId((byte) -1));
		assertThrows(IllegalArgumentException.class,
				() -> PlayerAction.byId((byte) PlayerAction.values().length));
	}

	private static RemoteServer roundTrip(RemoteServer server) throws IOException {
		PacketOut out = new PacketOut(Protocol.SERVER_INFO);
		server.write(out);
		return RemoteServer.read(out.frame().reader());
	}
}
