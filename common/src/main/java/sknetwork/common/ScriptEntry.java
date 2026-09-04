package sknetwork.common;

/**
 * One script in a manifest. The hash is what decides whether a backend already
 * holds it, so the bytes only move when they actually changed.
 *
 * @param path   relative to the scripts root, always using '/'
 * @param sha256 lowercase hex
 */
public record ScriptEntry(String path, String sha256) {
}
