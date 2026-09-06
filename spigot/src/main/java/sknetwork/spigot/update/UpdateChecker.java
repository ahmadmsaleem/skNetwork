package sknetwork.spigot.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import ch.njol.skript.util.Version;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import sknetwork.spigot.SknetStyle;


public final class UpdateChecker {

	private static final String REPO = "ahmadmsaleem/skNetwork";

	private static final String LATEST =
			"https://api.github.com/repos/" + REPO + "/releases/latest";

	private static volatile GitHubRelease available;

	public static void enable(JavaPlugin plugin) {
		plugin.getServer().getPluginManager().registerEvents(new JoinListener(plugin), plugin);
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			GitHubRelease newer = check(plugin);
			if (newer == null || !plugin.isEnabled())
				return;
			available = newer;
			plugin.getServer().getScheduler().runTask(plugin, () -> announce(plugin, newer));
		});
	}

	static GitHubRelease available() {
		return available;
	}

	private static GitHubRelease check(JavaPlugin plugin) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(LATEST))
					.header("Accept", "application/vnd.github+json")
					.header("User-Agent", "skNetwork/" + plugin.getPluginMeta().getVersion())
					.timeout(Duration.ofSeconds(10))
					.build();

			String body = HttpClient.newHttpClient()
					.send(request, HttpResponse.BodyHandlers.ofString())
					.body();
			JsonObject json = new Gson().fromJson(body, JsonObject.class);
			if (json == null || !json.has("tag_name"))
				return null;

			GitHubRelease latest = new GitHubRelease(json);
			Version running = new Version(plugin.getPluginMeta().getVersion());
			return latest.version().compareTo(running) > 0 ? latest : null;
		} catch (Exception failed) {
			plugin.getLogger().fine("update check failed: " + failed);
			return null;
		}
	}

	static List<Component> notice(JavaPlugin plugin, GitHubRelease newer) {
		return List.of(
				SknetStyle.brand("<white>a newer version is out"),
				SknetStyle.row("Running", "<white><version>",
						SknetStyle.text("version", plugin.getPluginMeta().getVersion())),
				SknetStyle.row("Latest", "<white><version>",
						SknetStyle.text("version", newer.version().toString())),
				SknetStyle.linkRow("Download", newer.link()));
	}

	private static void announce(JavaPlugin plugin, GitHubRelease newer) {
		notice(plugin, newer).forEach(Bukkit.getConsoleSender()::sendMessage);
	}

	private UpdateChecker() {
	}
}
