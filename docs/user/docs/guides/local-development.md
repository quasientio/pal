# Local Development Guide

This guide covers Chronicle-only workflows: developing, running, and debugging a single PAL peer locally without etcd or Kafka. If you're new to PAL, start with [Getting Started](../getting-started.md) — it walks through installation, project scaffolding (`pal init`), and a first run end-to-end. This guide picks up from there with patterns specific to fast local iteration.

## Direct Mode (`file:` prefix)

Chronicle Queue is a local file-based log backend. Anywhere a CLI command takes a log name, prefix it with `file:` and PAL talks directly to the file on disk — no PAL directory, no etcd, no service discovery:

```bash
# Run a peer, writing the WAL to a local Chronicle queue
pal run --wal file:/tmp/dev-wal -cp build/classes/java/main com.example.Main

# Print messages from that queue (no -d flag, no directory needed)
pal log print file:/tmp/dev-wal --tree

# Delete the queue when you're done
pal log rm file:/tmp/dev-wal
```

This is the easiest way to use PAL — no infrastructure, just files. The trade-off is no service discovery: peers can't find each other by name, so multi-peer scenarios need the directory. See the [Distributed Application Guide](distributed-application.md) when you're ready for that.

Other commands accept the `file:` prefix too, including `pal log call` (write a method-call message into a queue) and `pal log ls` (when run against a directory it can also report on Chronicle queues registered under it). See the [CLI Reference](../cli-reference.md) for the full list.

## Local Workflow

### Run with a Chronicle WAL

```bash
pal run --wal file:/tmp/dev-wal \
  -cp build/classes/java/main \
  com.example.HelloService hello world
```

Every operation in your code — method calls, field accesses, constructors — is captured as a message and appended to `/tmp/dev-wal`.

### Inspect the WAL

`pal log print` accepts a Chronicle path with the `file:` prefix and supports four output formats:

| Flag | Output |
|------|--------|
| `--compact` (default) | One message per line, terse |
| `--full` | Full message detail including arguments and return values |
| `--tree` | Indented tree showing call nesting |
| `--json` | Machine-readable JSON |

```bash
pal log print file:/tmp/dev-wal --tree
pal log print file:/tmp/dev-wal --full
```

### Replay or Re-Execute

Two commands re-run a recorded WAL, with different semantics:

```bash
# Re-dispatch the recorded messages into a fresh peer (no main class needed)
pal run --source-log file:/tmp/dev-wal -cp build/classes/java/main

# Re-execute the application from main() and verify against the recorded WAL
pal replay --wal file:/tmp/dev-wal \
  -cp build/classes/java/main \
  com.example.HelloService hello world
```

`pal run --source-log` simply replays the messages — useful for rebuilding state or driving a peer from a recorded session. `pal replay` re-runs the application's `main()` entry and compares each operation against the recorded log; exit code `0` means the execution matched, exit code `2` means divergences were detected. See [Deterministic Replay](../concepts/deterministic-replay.md) for full details.

## Debugging Locally

### Run from your IDE

To debug PAL applications under breakpoints, run the CLI directly from your IDE's run configuration:

- **Main class**: `io.quasient.pal.tools.cli.Pal`
- **Program arguments**: `run --wal file:/tmp/debug-wal -cp build/classes/java/main com.example.Main arg1`
- **Working directory**: project root
- **VM options**: any Chronicle/JDK module exports your version requires — see `bin/pal` for the canonical list

Set breakpoints in your application code; the AspectJ-woven dispatch path will surface them like any other Java code.

### Enable debug logging

PAL's runtime uses a shaded logback that's independent of your application's logging. To enable debug output for PAL internals, point `PAL_PEER_LOGGING_CONFIG` at a custom logback config:

```xml
<!-- .local/conf/peer-logging.xml -->
<configuration>
    <appender name="STDOUT" class="io.quasient.pal.common.logging.PeerConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="io.quasient.pal" level="DEBUG"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

```bash
export PAL_PEER_LOGGING_CONFIG=".local/conf/peer-logging.xml"
pal run --wal file:/tmp/debug-wal -cp build/classes/java/main com.example.Main
```

Your application's own logback/SLF4J configuration is unaffected.

### Follow the WAL live

Watch operations stream into the WAL as the peer runs:

```bash
# Terminal 1
pal run --wal file:/tmp/live-wal -cp build/classes/java/main com.example.Main

# Terminal 2
pal log print file:/tmp/live-wal -f --full
```

Every method call, field access, and return value scrolls past in real time.

## Tips

- **Use relative paths.** `--wal file:dev-wal` creates the queue in your current directory — easier than `/tmp/...` and easier to clean up.
- **Reuse WALs across runs.** A single recorded WAL can be re-run many times via `--source-log` (replay) or verified via `pal replay`. No need to re-create test data while debugging.
- **Skip JAR packaging during dev.** `-cp build/classes/java/main` is faster than `-cp build/libs/myapp.jar` because Gradle doesn't have to repackage the JAR after each compile.
- **Clean WALs between runs** when you want a fresh state: `rm -rf /tmp/dev-wal` (or `pal log rm file:/tmp/dev-wal`).

## When to Switch to Distributed

Stay with Chronicle locally when:

- You're developing a single-peer application.
- You're debugging application logic.
- You're writing or running unit-level tests.

Switch to etcd + Kafka when:

- You need to test multi-peer interaction or service discovery.
- You're testing interception across peers (intercept registration goes through etcd).
- You're preparing for production deployment.

For the full distributed setup, see [Distributed Application](distributed-application.md).

## Common Issues (Chronicle-Specific)

### "Chronicle queue does not exist"

You're trying to read from a queue that hasn't been created. Create it by writing first:

```bash
pal run --wal file:/tmp/my-queue -cp app.jar com.example.Main
pal run --source-log file:/tmp/my-queue -cp app.jar
```

### "Permission denied"

The current user can't write to the queue directory. Check ownership:

```bash
ls -la /tmp/my-queue
chmod -R u+rw /tmp/my-queue
```

### "Disk full"

Chronicle queues grow append-only and can fill a partition. Find and remove old ones:

```bash
du -sh /tmp/*
rm -rf /tmp/old-queue
```

## Further Reading

- [Getting Started](../getting-started.md) — installation, `pal init`, manual build setup
- [Log Backends](../concepts/logs.md) — Chronicle vs Kafka deep dive
- [Deterministic Replay](../concepts/deterministic-replay.md) — verifying re-execution against a recorded WAL
- [Distributed Application](distributed-application.md) — moving from Chronicle to etcd + Kafka
- [CLI Reference](../cli-reference.md) — full command and flag reference
