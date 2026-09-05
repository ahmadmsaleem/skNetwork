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
- **Change events.** `on network variable change of "coins::*"` fires on every server
  with the old and the new value.
- **Who is where.** `all network players`, `network server of "Notch"`,
  `network server "survival" is online`, plus the motd, version, player limit and
  whitelist of any server. Read from memory, kept fresh by the proxy.
- **Messages anywhere.** `send network message` and `send network action bar` to a
  player on any server, `broadcast ... across the network` to everyone.
- **Moving players.** `connect network player "%player%" to "survival"`.
- **Remote console.** `execute command ... on network server "survival"`. Off until
  the proxy config allows it.
- **Script sharing.** Push scripts from the proxy, with per-server load results.
- **Survives restarts.** Append-only log, self-compacting, reconnects resume.
- **BungeeCord and Velocity.** Same jar, same config.

## Requirements

| | Works with | Recommended |
|---|---|---|
| Java | **25** | 25 |
| Minecraft | 1.21 to 26.2 | 26.1.2 |
| Skript | 2.16.0+ | 2.16.2 |
| Proxy | BungeeCord or Velocity | either |

**Java 25 is required.** The jar will not load on Java 21 or older, whatever Minecraft
version you run. Both rules apply together: a 1.21 server also has to be started with
Java 25.

Every game server and the proxy run the same jar. One that is behind is refused
when it connects, with a message on both consoles saying which side to update.

Works on offline servers. skNetwork never looks at player identity.
Tested on Minecraft 1.21.11 and 26.1.2, Skript 2.16.2, BungeeCord 26.1 and
Velocity 4.1.1.

## Commands

```
/sknet                      state, proxy, copy size, latency
/sknet resync               pull the whole map again
/sknet reconnect            drop the connection and resume

/sknetproxy                 proxy: state, backends, variable count
/sknetproxy push            proxy: send scripts now
/sknetproxy dump <pattern>  proxy: look up variables, '*' is a wildcard
```

The two halves use different names on purpose. A proxy handles any command it knows
before the game server sees it, so keeping `/sknet` free means it always reaches the
server you are standing on, where being an operator is already enough.


---
skNetwork uses bStats for anonymous usage stats. You can opt out in `plugins/bStats/config.yml`.

### Bukkit / Spigot / Paper

[![bStats - Bukkit / Spigot](https://bstats.org/signatures/bukkit/skNetwork.svg)](https://bstats.org/plugin/bukkit/skNetwork/33851)


### BungeeCord

[![bStats - BungeeCord](https://bstats.org/signatures/bungeecord/skNetwork.svg)](https://bstats.org/plugin/bungeecord/skNetwork/33852)

### Velocity

[![bStats Velocity](https://bstats.org/signatures/velocity/skNetwork.svg)](https://bstats.org/plugin/velocity/skNetwork/33853)


Setup, config and the full syntax reference are in the
[wiki](https://github.com/ahmadmsaleem/skNetwork/wiki).
