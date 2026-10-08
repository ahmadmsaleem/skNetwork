package sknetwork.spigot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.config.Node;
import ch.njol.skript.log.LogEntry;
import ch.njol.skript.log.RetainingLogHandler;
import org.skriptlang.skript.lang.script.Script;

final class SkriptScripts {

	private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("skNetwork");


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

	/**
	 * Loads everything under {@code root} and hands the report to {@code whenDone} on
	 * {@code onMainThread}.
	 *
	 * Never joins the future. With 'script loader thread size' at 1 or more Skript parses
	 * off-thread and calls back to the main thread to register what it finds, so waiting
	 * here deadlocks the server outright.
	 *
	 * The handler is passed in unopened: loadScripts owns it for the load and closes it
	 * after, and what it retained is readable once the future completes.
	 */
	static void reload(File root, File barrier, Executor onMainThread, Consumer<LoadReport> whenDone) {
		afterQueuedLoads(barrier, onMainThread, () -> {
			try {
				unloadUnder(root);
			} catch (RuntimeException e) {
				whenDone.accept(new LoadReport(0,
						List.of(new LoadProblem(root.getName(), 0, String.valueOf(e), true))));
				return;
			}

			if (!root.isDirectory()) {
				whenDone.accept(new LoadReport(0, List.of()));
				return;
			}

			RetainingLogHandler handler = new RetainingLogHandler();
			try {
				ScriptLoader.loadScripts(root, handler).whenCompleteAsync((info, error) -> {
					LoadReport report;
					try {
						report = error != null
								? new LoadReport(0, List.of(new LoadProblem(root.getName(), 0, String.valueOf(error), true)))
								: new LoadReport(countLoadedUnder(root), collect(handler, root));
					} catch (RuntimeException e) {
						report = new LoadReport(0, List.of(new LoadProblem(root.getName(), 0, String.valueOf(e), true)));
					}
					whenDone.accept(report);
				}, onMainThread);
			} catch (RuntimeException e) {
				whenDone.accept(new LoadReport(0,
						List.of(new LoadProblem(root.getName(), 0, String.valueOf(e), true))));
			}
		});
	}

	/**
	 * Runs {@code then} on the main thread once Skript's loader has finished everything
	 * queued ahead of us.
	 *
	 * Skript loads this folder itself at startup. Unloading before that load has registered
	 * finds nothing, and the reload that follows leaves both copies registered: every trigger
	 * runs twice, and unloading later cannot reach the orphan. Queueing a throwaway script
	 * first, and unloading only once it is through, puts the unload behind Skript's own load.
	 * With async loading off the queue is not used and this adds a tick at most.
	 */
	private static void afterQueuedLoads(File barrier, Executor onMainThread, Runnable then) {
		try {
			Files.writeString(barrier.toPath(), "options:\n\tsknetwork_load_barrier: true\n");
		} catch (IOException e) {
			then.run();
			return;
		}

		try {
			ScriptLoader.loadScripts(Set.of(barrier), new RetainingLogHandler())
					.whenCompleteAsync((info, error) -> {
						// a callback that throws is swallowed by the future, and the load it was
						// guarding would then never happen
						try {
							if (error != null)
								LOG.warning("load barrier failed, loading without it: " + error);
							// getScripts(File) only takes a directory, so match the file ourselves
							Set<Script> loaded = new HashSet<>();
							for (Script script : ScriptLoader.getLoadedScripts()) {
								File file = script.getConfig().getFile();
								if (file != null && sameFile(file, barrier))
									loaded.add(script);
							}
							if (!loaded.isEmpty())
								ScriptLoader.unloadScripts(loaded);
						} catch (RuntimeException e) {
							LOG.log(Level.WARNING, "could not clear the load barrier", e);
						} finally {
							then.run();
						}
					}, onMainThread);
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "could not queue the load barrier, loading without it", e);
			then.run();
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

	private static boolean sameFile(File a, File b) {
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		} catch (IOException e) {
			return a.getAbsoluteFile().equals(b.getAbsoluteFile());
		}
	}

	private static boolean isUnder(File file, File root) {
		return file.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator);
	}

	private SkriptScripts() {
	}
}
