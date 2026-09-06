package sknetwork.spigot.update;

import ch.njol.skript.util.Version;
import com.google.gson.JsonObject;

// Credit to
// https://github.com/ShaneBeee/SkBee/tree/master/src/main/java/com/shanebeestudios/skbee/api/util/update for the original,
// https://github.com/3add/PacketEventsSK/tree/main/src/main/java/dev/threeadd/packeteventssk/update for the version it grew into,
// and https://github.com/AnOwlBe/Skript-LuckPerms/tree/master/src/main/java/owlbe/skriptLuckPerms/update, which this one is built on.

public final class GitHubRelease {

	private final Version version;
	private final String link;

	GitHubRelease(JsonObject json) {
		String tag = json.get("tag_name").getAsString();
		this.version = new Version(tag.startsWith("v") ? tag.substring(1) : tag);
		this.link = json.get("html_url").getAsString();
	}

	public Version version() {
		return version;
	}

	public String link() {
		return link;
	}
}
