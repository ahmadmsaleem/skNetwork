# Contributing to skNetwork

## Issues

Bugs and feature requests go here:

**https://github.com/ahmadmsaleem/skNetwork/issues**

Pick a template and fill it in. The bug report asks for `/sk info`, and reports
without it are hard to act on, because most problems come down to a version
difference between two servers. `/sknet` from the backend and `/sknetproxy` from the
proxy are worth pasting too.

Not sure whether it is a bug? Open the issue anyway. For questions about your own
scripts, read [Limitations](https://github.com/ahmadmsaleem/skNetwork/wiki/Limitations)
first.

## Building

Java 25 and nothing else. No Minecraft server needed to build or to test.

```
./gradlew build    # tests, then build/libs/skNetwork-<version>.jar
./gradlew test     # tests only
```

Keep the modules apart: `common` is the protocol with no Bukkit or Bungee imports,
`proxy` is the proxy half with no Bukkit imports, `spigot` is the backend half.

## Tests

Add one for what you change. `NetworkServerTest` runs a real server over a socket, so
most proxy behaviour can be pinned down without a Minecraft server. Anything needing a
running server is not covered, so test that by hand, ideally with two backends
writing at once.


## Changing the protocol

Both halves ship in one jar, so somebody will always be running a stale copy on one
side.

- Bump `Protocol.VERSION` whenever a frame changes shape. The handshake check turns a
  mismatch into a clear message instead of a garbled frame.
- `MutationMode` ordinals are the wire values. Add new modes at the end, never reorder
  or remove one. `REMOVE_ALL` and `RESET` are placeholders so the numbers around them
  stay fixed.

## Style

Match the file you are editing. Tabs. Comments say why, not what.

## Releasing

For maintainers: set the version in `gradle.properties`, then tag `v<version>` and
push the tag. The workflow checks the tag against the version, refuses snapshots, runs
the tests, and opens a draft release. Downloads go through
[Modrinth](https://modrinth.com/project/sknetwork), so collect the jar from the run's
artifacts and upload it there.
