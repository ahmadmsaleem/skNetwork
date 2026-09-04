![Cover](https://cdn.modrinth.com/data/iyNJtPOa/images/1ce92fbcf95485badaa205ee71422533f8affe85.jpeg)


A Skript addon for sharing variables and scripts across every server on a BungeeCord or Velocity network.

```applescript
# on the lobby
set {?coins::%player%} to 100

# on survival, straight away, no extra code
send "You have %{?coins::%player%}% coins"
```

Any variable starting with `?` is a network variable. Reads come from this
server's own copy, so only writes go over the wire.

---

## Features

- **Shared variables.** Any `{?variable}` is live on every server.
- **Free reads.** Served from local memory. Only writes cross the wire.
- **Atomic changes.** `add`, `remove`, `set if not set`, `set if it is`. Worked out
  on the proxy, so nothing is lost when two servers race.
- **A spend floor.** `without going below 0` refuses instead of going negative.
- **Read the outcome.** `and wait` gives you the new value, or why it was refused.
- **Server identity.** `network server name`, for one script that runs everywhere.
- **Sync state.** `network is synced`, `on network sync`, `on network disconnect`.
- **Script sharing.** Push scripts from the proxy, with per-server load results.
- **Survives restarts.** Append-only log, self-compacting, reconnects resume.
- **BungeeCord and Velocity.** Same jar, same config.

## Commands

```
/sknet                 state, proxy, copy size, latency
/sknet resync          pull the whole map again
/sknet reconnect       drop the connection and resume

/sknet push            proxy: send scripts now
/sknet dump <pattern>  proxy: look up variables, '*' is a wildcard
```

---

Setup, config and the full syntax reference are going in the wiki.
