package sknetwork.spigot;

/** Connection lifecycle. Writes are only accepted in READY. */
public enum SyncState {

	DISCONNECTED,
	CONNECTING,
	SYNCING,
	READY
}
