package sknetwork.spigot.elements.effects;


import ch.njol.skript.bukkitutil.SoundUtils;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import sknetwork.common.NetworkSound;
import sknetwork.common.PacketOut;
import sknetwork.common.PlayerAction;
import sknetwork.spigot.SkNetworkSpigot;
import sknetwork.spigot.elements.types.NetworkPlayer;

@Name("Network Sound")
@Description("""
		Plays a sound to a player anywhere on the network, whichever server they are on.
		The sound is played at that player's own location, so it follows them rather than coming from a fixed point. A player nobody is holding is skipped.
		Names are the same ones `play sound` takes, so a resource pack sound works if that player's server has the pack.
		
		Guide: https://github.com/ahmadmsaleem/skNetwork/wiki/Network-Players
		""")
@Example("""
		play network sound "entity.player.levelup" to network player "Notch"
		""")
@Example("""
		play network sound "block.note_block.pling" with volume 0.5 and pitch 2 across the network
		""")
@Since("1.0.0")
public class EffNetworkSound extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffNetworkSound.class)
				.supplier(EffNetworkSound::new)
				.addPatterns(
						"play network sound %string% [(at|with) volume %-number%] "
								+ "[(and|at|with) pitch %-number%] to network player[s] %networkplayers%",
						"play network sound %string% [(at|with) volume %-number%] "
								+ "[(and|at|with) pitch %-number%] (across|to) [the] network")
				.build());
	}

	private Expression<String> sound;
	private Expression<Number> volume;
	private Expression<Number> pitch;
	private Expression<NetworkPlayer> targets;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
			@NotNull ParseResult result) {
		sound = (Expression<String>) exprs[0];
		volume = (Expression<Number>) exprs[1];
		pitch = (Expression<Number>) exprs[2];
		if (matchedPattern == 0)
			targets = (Expression<NetworkPlayer>) exprs[3];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		SkNetworkSpigot plugin = SkNetworkSpigot.get();
		String named = sound == null ? null : sound.getSingle(event);
		if (plugin == null || named == null)
			return;

		NamespacedKey key = SoundUtils.getKey(named);
		NetworkSound payload = new NetworkSound(key == null ? named : key.asString(),
				number(volume, event, 1f), number(pitch, event, 1f));

		PacketOut body = PacketOut.body();
		payload.write(body);

		NetworkTargets.Targets to = NetworkTargets.of(targets, event);
		plugin.playerAction(PlayerAction.SOUND, to.everyone(), to.names(), body.payload());
	}

	private static float number(Expression<Number> expression, Event event, float fallback) {
		if (expression == null)
			return fallback;
		Number value = expression.getSingle(event);
		return value == null ? fallback : value.floatValue();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "play network sound " + sound.toString(event, debug)
				+ (targets == null ? " across the network" : " to " + targets.toString(event, debug));
	}
}
