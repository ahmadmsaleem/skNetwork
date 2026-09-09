package sknetwork.spigot.elements.events;

import java.util.regex.Pattern;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sknetwork.spigot.SkriptBridge;

public class EvtNetworkVariable extends SkriptEvent {

	private Pattern wanted;

	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, @NotNull ParseResult parseResult) {
		if (args.length == 0 || args[0] == null)
			return true;

		Object glob = args[0].getSingle();
		if (glob != null)
			wanted = compile(SkriptBridge.normalize(glob.toString()));
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		if (!(event instanceof NetworkVariableChangeEvent change))
			return false;
		return wanted == null || wanted.matcher(change.variable()).matches();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "network variable change" + (wanted == null ? "" : " of " + wanted.pattern());
	}

	private static Pattern compile(String glob) {
		StringBuilder regex = new StringBuilder();
		int from = 0;
		for (int star = glob.indexOf('*'); star >= 0; star = glob.indexOf('*', from)) {
			if (star > from)
				regex.append(Pattern.quote(glob.substring(from, star)));
			regex.append(".*");
			from = star + 1;
		}
		if (from < glob.length())
			regex.append(Pattern.quote(glob.substring(from)));
		return Pattern.compile(regex.toString(), Pattern.DOTALL);
	}
}
