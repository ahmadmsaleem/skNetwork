package sknetwork.common;

/**
 * The only Skript naming rule either half understands: {@code ::} separates a
 * hierarchy, so {@code x::*} means x and everything beneath it.
 */
public final class VariableName {


	public static final String SEPARATOR = "::";
	public static final String TREE_SUFFIX = SEPARATOR + "*";

	public static boolean isTree(String name) {
		return name.endsWith(TREE_SUFFIX);
	}

	public static String treeBase(String name) {
		return isTree(name) ? name.substring(0, name.length() - TREE_SUFFIX.length()) : name;
	}

	/** @param base a {@link #treeBase(String)}, so an empty one means the whole tree */
	public static boolean inTree(String name, String base) {
		return base.isEmpty() || name.equals(base) || name.startsWith(base + SEPARATOR);
	}

	private VariableName() {
	}
}
