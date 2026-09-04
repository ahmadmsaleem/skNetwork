# Tests

311 JUnit tests over the parts of skNetwork that run without a Minecraft server:
the wire protocol, the proxy's writer thread, the change log, script distribution,
and the two Skript-side classes that touch neither Bukkit nor Skript.

## Running them

```
./gradlew :test:test          # just the tests
./gradlew test                # same thing from the root
./gradlew build               # tests plus the universal jar
```

One class:

```
./gradlew :test:test --tests 'sknetwork.proxy.core.NetworkServerTest'
```

One method:

```
./gradlew :test:test --tests '*.refusesToSpendBelowTheFloor'
```

Gradle skips the task when nothing changed. `--rerun-tasks` forces it.

The report lands in `test/build/reports/tests/test/index.html`.

## In CI

[`.github/workflows/tests.yml`](../.github/workflows/tests.yml) runs the same command
on every pull request, on pushes to `main` and `dev`, and on demand from the Actions
tab. It also builds the universal jar, so a change that passes the tests but breaks
packaging still fails the run. The report is uploaded as an artifact either way.

Put `[ci skip]` in a commit message to skip the run.

## What is covered

| Package | What the tests cover |
|---|---|
| `sknetwork.common` | varints, frames, packets, variable names, script paths, manifests, durations, display strings, throttling, mutation opcodes, chat formatting |
| `sknetwork.proxy.core` | number handling, the CSV line format, the variable store, server groups, the change log, the script library, settings, and the proxy end to end over a real socket |
| `sknetwork.spigot` | parked atomic changes, and the patch written into Skript's config.sk |

`NetworkServerTest` is the one worth knowing about. It starts a real `NetworkServer`
on a free port and connects `FakeBackend`, which speaks the protocol over a plain
socket. So the handshake, snapshots, deltas, replay after a reconnect, atomic
arithmetic and script pushes all go over TCP, not through a mock.
`letsOnlyOneOfTwoRacingSpendsThrough` is the lost update the whole design exists to
stop, written down.

## What is not

Anything that needs Bukkit, Paper or Skript loaded. `SkriptBridge` serialises values
through Skript's own classes, `DeltaApplier` extends `BukkitRunnable`, and the syntax
elements need a parser. Covering those means booting a server, the way Skript's own
suite does. The test network in `../skNetwork-test-server` is there for that, by hand
for now.

## Adding a test

Same package as the class under test, so package-private types stay reachable. No
comments: if a test needs one, the name is wrong.

Existing helpers:

- `RecordingLog` collects log lines, with `sawAny` and `sawWarning` to check them.
- `MapConfig` is a `ConfigSource` backed by a map, for settings.
- `FakeBackend` is a backend server on a socket.

Assertions about a log line need `awaitLog` or `awaitWarning` in `NetworkServerTest`.
The proxy logs from its writer thread, after the frame the test is waiting on has
already gone out, so a bare `assertTrue(log.sawAny(...))` is a race that passes most
of the time.
