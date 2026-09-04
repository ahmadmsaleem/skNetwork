package sknetwork.common;

/**
 * Validates the paths the proxy sends. The proxy supplies these as plain
 * strings, so the backend has to assume they are hostile until checked.
 */
public final class ScriptPath {


	public static final String EXTENSION = ".sk";

	/** Longest path we will accept, so a manifest cannot be used to exhaust the disk. */
	private static final int MAX_LENGTH = 200;

	/**
	 * @return true if the path is safe to resolve under the scripts folder
	 */
	public static boolean isSafe(String path) {
		if (path == null || path.isBlank() || path.length() > MAX_LENGTH)
			return false;
		if (!path.endsWith(EXTENSION))
			return false;
		if (path.startsWith("/") || path.startsWith("\\") || path.contains(":"))
			return false;
		if (path.contains("\\"))
			return false;

		for (String segment : path.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
				return false;
			// a leading dash tells Skript the script is disabled
			if (segment.startsWith("-"))
				return false;
		}
		return true;
	}

	private ScriptPath() {
	}
}
