package sknetwork.spigot.elements.types;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.util.UUID;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.converter.Converters;

public final class NetworkPlayerType {

	public static void register() {
		if (Classes.getExactClassInfo(NetworkPlayer.class) != null)
			return;

		Classes.registerClass(new ClassInfo<>(NetworkPlayer.class, "networkplayer")
				.user("network ?players?")
				.name("Network Player")
				.description("""
						Somebody on the network, wherever they are. Accepts a name, a UUID or a real player.
						Reads come from the player list the proxy already pushes to this server, so nothing waits on the wire.
						""")
				.usage("network player \"Notch\"")
				.examples("""
						set {_who} to network player "Notch"
						send "%{_who}% is on %network server of {_who}%"
						""")
				.since("1.0.0")
				.parser(new Parser<NetworkPlayer>() {

					@Override
					public @Nullable NetworkPlayer parse(@NotNull String input, @NotNull ParseContext context) {
						return NetworkPlayer.parse(input);
					}

					@Override
					public boolean canParse(@NotNull ParseContext context) {
						return true;
					}

					@Override
					public @NotNull String toString(@NotNull NetworkPlayer player, int flags) {
						return player.display();
					}

					@Override
					public @NotNull String toVariableNameString(@NotNull NetworkPlayer player) {
						return NetworkPlayers.variableName(player);
					}
				})
				.serializer(new Serializer<NetworkPlayer>() {

					@Override
					public @NotNull Fields serialize(@NotNull NetworkPlayer player) {
						Fields fields = new Fields();
						fields.putObject("name", player.name());
						UUID uuid = player.uuid();
						fields.putObject("uuid", uuid == null ? null : uuid.toString());
						return fields;
					}

					@Override
					public void deserialize(@NotNull NetworkPlayer player, @NotNull Fields fields)
							throws NotSerializableException {
						throw new NotSerializableException();
					}

					@Override
					protected @NotNull NetworkPlayer deserialize(@NotNull Fields fields)
							throws StreamCorruptedException {
						String name = fields.getObject("name", String.class);
						String uuid = fields.getObject("uuid", String.class);

						NetworkPlayer player = uuid == null
								? NetworkPlayer.named(name)
								: NetworkPlayer.known(name, UUID.fromString(uuid));
						if (player == null)
							throw new StreamCorruptedException();
						return player;
					}

					@Override
					public boolean mustSyncDeserialization() {
						return false;
					}

					@Override
					protected boolean canBeInstantiated() {
						return false;
					}
				}));

		Converters.registerConverter(Player.class, NetworkPlayer.class,
				player -> NetworkPlayer.known(player.getName(), player.getUniqueId()));
		Converters.registerConverter(OfflinePlayer.class, NetworkPlayer.class,
				player -> NetworkPlayer.known(player.getName(), player.getUniqueId()));
		Converters.registerConverter(String.class, NetworkPlayer.class, NetworkPlayer::parse);
	}

	private NetworkPlayerType() {
	}
}
