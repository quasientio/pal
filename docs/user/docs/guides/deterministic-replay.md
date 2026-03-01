# Deterministic Replay

PAL's WAL captures every quantized operation and its return value. Deterministic replay exploits this: it re-executes the application from `main()` while verifying every operation against the WAL. If the code hasn't changed, every operation produces the same result. If the code has changed, the differences surface as divergences.

## What Replay Is (and Isn't)

Replay is **not** playing back a recording. The application runs naturally — the same `main()` entry point, the same AspectJ call sites, the same JVM execution. The WAL is a companion data structure consulted at each dispatch point. This means:

- Your code runs for real. Breakpoints work. Logging works. Debuggers attach normally.
- The WAL is the oracle that says "this operation should produce this result."
- Divergences mean the live execution differs from the recording.

This is distinct from the existing `--source-log` mechanism, which re-dispatches individual operations from the WAL. Replay is application-driven; the application controls execution flow and the WAL verifies it.

## When to Use Replay

**Verifying code changes**: Record a WAL from a known-good execution, then replay after modifying code. Divergences pinpoint exactly where behavior changed.

**Reproducing bugs**: Record a failing execution, then replay it under a debugger. The same inputs produce the same bug, deterministically.

**Regression testing**: Capture WALs from important scenarios and replay them as part of CI. A zero-divergence replay proves the code still behaves identically.

**Understanding execution**: Use `pal wal-index` to inspect a WAL's structure, then replay to step through the execution in context.

## Prerequisites

- The application must be compiled with AspectJ weaving (same `pal-weave` aspect library as during recording)
- The same classpath should be used for recording and replay
- No infrastructure is needed — replay reads from the WAL directly (Chronicle or Kafka) and does not require etcd, Kafka brokers, or network access beyond reading the WAL

## Recording a WAL

Run the application with `--wal` to capture a WAL:

```bash
pal run --wal file:/tmp/my-wal \
  -cp target/classes com.example.App arg1 arg2
```

This records every quantized operation and its return value to a Chronicle Queue at `/tmp/my-wal`.

For Kafka:

```bash
pal run -d localhost:2379 -k localhost:29092 --wal my-topic \
  -cp app.jar com.example.App arg1 arg2
```

## Replaying from a WAL

### Basic Replay

```bash
pal replay --wal file:/tmp/my-wal \
  -cp target/classes com.example.App arg1 arg2
```

If the application produces the same operations with the same return values, the exit code is `0` and no divergences are reported.

### Replay from Kafka

```bash
# With explicit Kafka servers
pal replay --wal my-topic -k localhost:29092 \
  -cp app.jar com.example.App arg1 arg2

# With PAL directory (resolves servers automatically)
pal replay -d localhost:2379 --wal my-topic \
  -cp app.jar com.example.App arg1 arg2
```

## Understanding Divergences

When the live execution differs from the WAL, divergences are reported to stderr after the application completes. The exit code is `2`.

### Divergence Types

| Type | Meaning |
|------|---------|
| `VALUE_MISMATCH` | The operation executed but returned a different value than recorded |
| `TYPE_MISMATCH` | The return value has a different type than recorded |
| `OPERATION_MISMATCH` | The next live operation doesn't match the expected WAL entry (different class, method, or parameter types) |
| `EXTRA_OPERATION` | The application performed an operation not present in the WAL |
| `MISSING_OPERATION` | The WAL expected an operation that the application didn't perform |

### Example: Detecting a Code Change

```bash
# Record baseline
pal run --wal file:/tmp/baseline -cp target/classes com.example.Calculator "2+3"
# Output: 5

# Modify the code (e.g., change add to multiply)
mvn compile

# Replay against baseline
pal replay --wal file:/tmp/baseline -cp target/classes com.example.Calculator "2+3"
# Exit code: 2
# stderr: [VALUE_MISMATCH] offset=42: expected "5" but got "6"
```

### Example: Different Inputs

Recording with one input and replaying with another will produce divergences wherever the execution paths differ:

```bash
pal run --wal file:/tmp/recorded -cp target/classes com.example.App "hello"
pal replay --wal file:/tmp/recorded -cp target/classes com.example.App "goodbye"
# Exit code: 2 (arguments flow through the code and produce different return values)
```

## Divergence Policies

Control how the replay system responds to divergences:

```bash
# Default: log divergences and continue
pal replay --wal file:/tmp/my-wal -cp target/classes com.example.App

# Stop on first divergence (useful with debugger attached)
pal replay --wal file:/tmp/my-wal --divergence-policy HALT \
  -cp target/classes com.example.App

# Silently record divergences (check exit code only)
pal replay --wal file:/tmp/my-wal --divergence-policy IGNORE \
  -cp target/classes com.example.App
```

## Inspecting WAL Structure

Before replaying, you can inspect a WAL to understand its structure:

```bash
# Summary: entry counts, pairing, threads, structural issues
pal wal-index file:/tmp/my-wal
```

```
WAL Index Summary
  Entries:     142
  Operations:  71
  Completions: 71
  Pairs:       71
  Threads:     [main]
  Issues:      0
```

A healthy WAL has equal Operations and Completions, all entries paired, and zero issues. Issues indicate the application was interrupted mid-execution.

For per-entry detail:

```bash
pal wal-index --verbose file:/tmp/my-wal
```

This shows every entry with its offset, kind (OPERATION or COMPLETION), thread name, and operation signature. Combined with `pal print --tree`, this gives a complete picture of the recorded execution.

## Debugging with Replay

Replay pairs naturally with standard Java debuggers. Since the application runs normally during replay, you can set breakpoints, inspect variables, and step through code.

### Attaching a Debugger

Use the `HALT` divergence policy so the replay stops at the first difference, then step through:

```bash
# Start replay with debug port
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" \
  pal replay --wal file:/tmp/my-wal --divergence-policy HALT \
  -cp target/classes com.example.App
```

Connect your IDE debugger to port 5005 and step through the code. The replay is transparent to the debugger — it looks like normal execution, but every operation is verified against the WAL.

## Complete Workflow Example

```bash
# 1. Build the application
mvn clean compile

# 2. Record a known-good execution
pal run --wal file:/tmp/golden-run \
  -cp target/classes com.example.OrderProcessor "TX|A|gum=1.00|0|"

# 3. Inspect the WAL
pal wal-index file:/tmp/golden-run

# 4. Verify replay matches (sanity check)
pal replay --wal file:/tmp/golden-run \
  -cp target/classes com.example.OrderProcessor "TX|A|gum=1.00|0|"
# Exit code: 0

# 5. Make a code change
vim src/main/java/com/example/OrderProcessor.java
mvn compile

# 6. Replay to find what changed
pal replay --wal file:/tmp/golden-run \
  -cp target/classes com.example.OrderProcessor "TX|A|gum=1.00|0|"
# Exit code: 2 — divergences show exactly where behavior differs
```

## Current Limitations

- **Single-threaded applications only**: Replay currently supports the self-caller thread (the thread running `main()`). Multi-threaded replay (for web apps, RPC services, Swing applications) is planned.
- **RE_EXECUTE only**: All operations are re-executed and verified. Side-effect shielding (stubbing I/O, databases, time functions from the WAL without executing them) is planned.
- **Same code version expected**: Replay works best when the recorded and replayed code are identical or have small, targeted changes. Large structural changes will produce many operation-mismatch divergences.
- **No WAL output during replay**: Replay is read-only. A new WAL is not written during replay. This will be addressed when replay is integrated as a layer within the normal dispatch path.

## Further Reading

- [CLI Reference](../cli-reference.md) — Complete option documentation for `pal replay` and `pal wal-index`
- [Local Development Guide](local-development.md) — Recording WALs during development
- [Log Backends](../concepts/logs.md) — Chronicle Queue vs Kafka for WAL storage
