package sknetwork.spigot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import sknetwork.common.Style;

/**
* How {@code /sknet} prints on a backend. MiniMessage, because Paper renders it the
* same way for a player and for the console, and it carries a gradient and a text
* shadow that legacy section codes cannot.
* <p>
* The proxy half stays on {@link Style} and its legacy codes. BungeeCord ships no
* Adventure of its own, so that is still the one format both proxies can be handed.
* <p>
* Anything that comes from a config, another server or an exception goes in through
* {@link #text}, so a stray {@code <} in it is printed rather than parsed as a tag.
*/
public final class SknetStyle {

	private static final MiniMessage MM = MiniMessage.miniMessage();

	/** The addon's own green, from the logo, and the light end of the wordmark. */
	private static final String BRAND = "#" + Style.BRAND_HEX;
	private static final String BRAND_LIGHT = "#E4F5A9";

	/**
	* Text shadows landed in 1.21.4. An older server's MiniMessage does not know the
	* tag, and prints an unknown one as words in the middle of the line rather than
	* failing, so ask it once at startup instead of finding out in chat.
	*/
	private static final String GLOW = StandardTags.defaults().has("shadow")
			? "<shadow:" + BRAND + ":0.3>"
			: "";

	/** Brand green over its own shadow, the accent every line is picked out in. */
	private static final String ACCENT = GLOW + "<" + BRAND + ">";

	/** Left edge every line hangs off, so the block reads as one thing. */
	private static final String EDGE = ACCENT + "│<reset> ";

	/** Width the labels are padded to. Even in chat's variable font this lines up well. */
	private static final int LABEL_WIDTH = 11;

	/** {@code [skNetwork] 0.2.0 · protocol 6}, the wordmark in the logo's green. */
	public static Component header(String version, int protocol) {
		return MM.deserialize("<dark_gray>[" + GLOW + "<gradient:" + BRAND + ":" + BRAND_LIGHT
				+ ":" + BRAND + "><b>skNetwork</b><reset><dark_gray>]<reset>"
				+ "  <gray><version>  <dark_gray>·  <gray>protocol <white><protocol>",
				text("version", version),
				text("protocol", Integer.toString(protocol)));
	}

	/** A blank line inside the block, so it still shows the edge. */
	public static Component gap() {
		return MM.deserialize(ACCENT + "│");
	}

	/**
	* One labelled line. The value is a MiniMessage template, so a row can colour
	* itself for a state that can be bad, with everything variable in it passed as a
	* {@link #text} placeholder.
	*/
	public static Component row(String label, String value, TagResolver... values) {
		return MM.deserialize(EDGE + "<gray><label><reset>" + value,
				TagResolver.resolver(values), text("label", pad(label)));
	}

	/** {@code /sknet resync   pull the whole map again}, click to put it in the box. */
	public static Component hint(String command, String description) {
		// the command is ours, never anything typed, so it can go straight into the
		// click tag. a placeholder cannot: MiniMessage does not resolve one in there.
		return MM.deserialize(EDGE + "<click:suggest_command:'" + command + "'>"
				+ "<hover:show_text:'<gray>click to type it'>" + ACCENT + "<cmd>"
				+ "</hover></click><reset>  <gray><desc>",
				text("cmd", command), text("desc", description));
	}

	public static Component note(String note, TagResolver... values) {
		return MM.deserialize(EDGE + "<dark_gray>" + note, values);
	}

	/** A value that is text, not markup: whatever is in it is printed as it stands. */
	public static TagResolver text(String key, String value) {
		return Placeholder.unparsed(key, value);
	}

	private static String pad(String label) {
		if (label.length() >= LABEL_WIDTH)
			return label + " ";
		return label + " ".repeat(LABEL_WIDTH - label.length());
	}

	private SknetStyle() {
	}
}
