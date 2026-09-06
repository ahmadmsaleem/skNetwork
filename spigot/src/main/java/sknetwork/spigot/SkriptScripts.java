package sknetwork.spigot;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.config.Node;
import ch.njol.skript.log.LogEntry;
import ch.njol.skript.log.RetainingLogHandler;
import org.skriptlang.skript.lang.script.Script;

final class SkriptScripts {


	/**
	 * One thing Skript complained about while loading.
	 *
	 * @param severe whether it stopped something loading, as opposed to a style
	 * warning that did not. Reporting the two the same way sends an
	 * admin hunting for a broken script that loaded perfectly well.
	 */
	record LoadProblem(String path, int line, String message, boolean severe) {
	}

	record LoadReport(int loaded, List<LoadProblem> problems) {

		List<LoadProblem> errors() {
			return problems.stream().filter(LoadProblem::severe).toList();
		}

		List<LoadProblem> warnings() {
			return problems.stream().filter(problem -> !problem.severe()).toList();
		}
	}

	static LoadReport reload(File root) {
		unloadUnder(root);

		if (!root.isDirectory())
			return new LoadReport(0, List.of());

		try (RetainingLogHandler handler = new RetainingLogHandler().start()) {
			ScriptLoader.loadScripts(root, handler).join();
			return new LoadReport(countLoadedUnder(root), collect(handler, root));
		} catch (RuntimeException e) {
			return new LoadReport(0, List.of(new LoadProblem(root.getName(), 0, String.valueOf(e), true)));
		}
	}

	static void unloadUnder(File root) {
		Set<Script> ours = new HashSet<>();
		for (Script script : ScriptLoader.getLoadedScripts()) {
			File file = script.getConfig().getFile();
			if (file != null && isUnder(file, root))
				ours.add(script);
		}
		if (!ours.isEmpty())
			ScriptLoader.unloadScripts(ours);
	}

	private static int countLoadedUnder(File root) {
		int count = 0;
		for (Script script : ScriptLoader.getLoadedScripts()) {
			File file = script.getConfig().getFile();
			if (file != null && isUnder(file, root))
				count++;
		}
		return count;
	}


	private static List<LoadProblem> collect(RetainingLogHandler handler, File root) {
		List<LoadProblem> problems = new ArrayList<>();
		for (LogEntry entry : handler.getLog()) {
			if (entry.level.intValue() < Level.WARNING.intValue())
				continue;
			problems.add(new LoadProblem(pathOf(entry, root), lineOf(entry), entry.getMessage(),
					entry.level.intValue() >= Level.SEVERE.intValue()));
		}
		return problems;
	}

	private static String pathOf(LogEntry entry, File root) {
		Node node = entry.node;
		// some warnings arrive with no node, and the message names the file itself
		if (node == null || node.getConfig() == null)
			return "";

		File file = node.getConfig().getFile();
		if (file == null)
			return node.getConfig().getFileName();

		String path = file.getAbsolutePath();
		String base = root.getAbsolutePath();
		return path.startsWith(base) ? path.substring(base.length() + 1).replace(File.separatorChar, '/')
				: file.getName();
	}

	private static int lineOf(LogEntry entry) {
		Node node = entry.node;
		return node == null ? 0 : Math.max(node.getLine(), 0);
	}

	private static boolean isUnder(File file, File root) {
		return file.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator);
	}

	private SkriptScripts() {
	}
}
