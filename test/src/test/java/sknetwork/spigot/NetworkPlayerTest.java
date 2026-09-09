package sknetwork.spigot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sknetwork.common.PacketIn;
import sknetwork.common.PacketOut;
import sknetwork.common.Protocol;
import sknetwork.common.RemoteServer;
import sknetwork.spigot.elements.types.NetworkPlayer;

class NetworkPlayerTest {

	private static final String UUID_TEXT = "8b7d154a-1315-4356-a070-2bdc008ac617";

	@Test
	void readsANameAsANameAndAUuidAsAUuid() {
		NetworkPlayer named = NetworkPlayer.parse("Notch");
		assertEquals("Notch", named.name());
		assertNull(named.uuid());

		NetworkPlayer byUuid = NetworkPlayer.parse(UUID_TEXT);
		assertNull(byUuid.name());
		assertEquals(UUID.fromString(UUID_TEXT), byUuid.uuid());
		assertEquals(UUID_TEXT, byUuid.display());
	}

	@Test
	void treatsTheSameNameInAnyCaseAsOnePerson() {
		assertEquals(NetworkPlayer.parse("Notch"), NetworkPlayer.parse("notch"));
		assertEquals(NetworkPlayer.parse("Notch").hashCode(), NetworkPlayer.parse("NOTCH").hashCode());
		assertNotEquals(NetworkPlayer.parse("Notch"), NetworkPlayer.parse("Njol"));
	}

	@Test
	void takesOnlyAPlausibleNameOrAUuid() {
		assertEquals("Notch", NetworkPlayer.parse("Notch").name());
		assertEquals(UUID.fromString(UUID_TEXT), NetworkPlayer.parse(UUID_TEXT).uuid());
		assertEquals(".BedrockName", NetworkPlayer.parse(".BedrockName").name());

		assertNull(NetworkPlayer.parse("totally bogus unquoted text here"));
		assertNull(NetworkPlayer.parse("\"&aZZZ\" across the network"));
		assertNull(NetworkPlayer.parse("a-name-far-too-long-to-be-real"));
		assertNull(NetworkPlayer.parse(""));
	}

	@Test
	void judgesAConvertedStringTheSameWayAsATypedLiteral() {
		assertNull(NetworkPlayer.parse("hello world"),
				"a string that cannot be a name must not become a network player");
		assertEquals(NetworkPlayer.parse("Notch"), NetworkPlayer.parse("Notch"));
	}

	@Test
	void refusesNothingToIdentifySomebodyBy() {
		assertNull(NetworkPlayer.parse(null));
		assertNull(NetworkPlayer.parse("  "));
		assertNull(NetworkPlayer.named(""));
	}

	@Test
	void keepsBothHalvesWhenTheyAreKnown() {
		NetworkPlayer player = NetworkPlayer.known("Notch", UUID.fromString(UUID_TEXT));

		assertEquals("Notch", player.name());
		assertEquals(UUID.fromString(UUID_TEXT), player.uuid());
		assertEquals("Notch", player.display());
	}

	@Test
	void buildsPlayersFromTheListTheProxyPushes() throws IOException {
		NetworkCache cache = new NetworkCache();
		cache.replace(frame(server("survival", "c"), server("Lobby", "a", "b")));

		assertEquals(List.of(NetworkPlayer.named("a"), NetworkPlayer.named("b")),
				cache.networkPlayers("Lobby"));
		assertEquals(3, cache.networkPlayers(null).size());
		assertTrue(cache.networkPlayers("ghost").isEmpty());
		assertNull(cache.networkPlayers("Lobby").get(0).uuid());
	}

	private static PacketIn frame(RemoteServer... servers) {
		PacketOut out = new PacketOut(Protocol.NETWORK_STATE).varInt(servers.length);
		for (RemoteServer server : servers)
			server.write(out);
		return new PacketIn(out.frame().payload);
	}

	private static RemoteServer server(String name, String... players) {
		return new RemoteServer(name, "m", "v", 20, List.of(players), List.of());
	}
}
