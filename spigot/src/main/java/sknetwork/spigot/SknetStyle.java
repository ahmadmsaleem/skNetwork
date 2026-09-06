package sknetwork.spigot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import sknetwork.common.Style;

public final class SknetStyle {

	private static final MiniMessage MM = MiniMessage.miniMessage();

	/** The addon's own green, from the logo, and the light end of the wordmark. */
	private static final String BRAND = "#" + Style.BRAND_HEX;
	private static final String BRAND_LIGHT = "#E4F5A9";

	private static final String GLOW = StandardTags.defaults().has("shadow")
			? "<shadow:" + BRAND + ":0.3>"
			: "";

	private static final String ACCENT = GLOW + "<" + BRAND + ">";

	private static final String WORDMARK = "<dark_gray>[<gradient:" + BRAND + ":"
			+ BRAND_LIGHT + ":" + BRAND + ">skNetwork<reset><dark_gray>]<reset>";

	private static final String EDGE = ACCENT + "│<reset> ";

	private static final int LABEL_WIDTH = 11;

	public static Component header(String version, int protocol) {
		return render(WORDMARK + "  <gray><version>  <dark_gray>-  <gray>protocol <white><protocol>",
				text("version", version),
				text("protocol", Integer.toString(protocol)));
	}

	public static Component brand(String line, TagResolver... values) {
		return render(WORDMARK + " " + line, values);
	}

	public static Component gap() {
		return render(ACCENT + "│");
	}

	public static Component row(String label, String value, TagResolver... values) {
		return render(EDGE + "<gray><label><reset>" + value,
				TagResolver.resolver(values), text("label", pad(label)));
	}

	public static Component hover(Component line, String help) {
		return line.hoverEvent(render("<gray><help>", text("help", help)));
	}

	public static Component linkRow(String label, String url) {
		return render(EDGE + "<gray><label>", text("label", pad(label)))
				.append(render(ACCENT + "<url>", text("url", url))
						.clickEvent(ClickEvent.openUrl(url))
						.hoverEvent(Component.text("open in your browser")));
	}

	public static Component hint(String command, String description) {
		return render(EDGE + "<click:suggest_command:'" + command + "'>"
				+ "<hover:show_text:'<gray>click to type it'>" + ACCENT + "<cmd>"
				+ "</hover></click><reset>  <gray><desc>",
				text("cmd", command), text("desc", description));
	}

	public static Component note(String note, TagResolver... values) {
		return render(EDGE + "<dark_gray>" + note, values);
	}

	public static TagResolver text(String key, String value) {
		return Placeholder.unparsed(key, value);
	}

	private static Component render(String template, TagResolver... values) {
		return MM.deserialize(template, values);
	}

	private static String pad(String label) {
		if (label.length() >= LABEL_WIDTH)
			return label + " ";
		return label + " ".repeat(LABEL_WIDTH - label.length());
	}

	private SknetStyle() {
	}
}
