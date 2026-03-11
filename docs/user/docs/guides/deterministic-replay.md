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

### Recording RPC Applications

If your application receives input via RPC (ZMQ, JSON-RPC, or WebSocket), ensure that `--wal-incoming-rpc` is enabled during recording. This is the default, but if you have previously disabled it, re-enable it:

```bash
pal run --wal file:/tmp/service-wal --wal-incoming-rpc \
  --json-rpc auto --rpc-threads 4 \
  -cp target/classes com.example.ServiceMain
```

With `--wal-incoming-rpc`, each incoming RPC call is recorded as an **entry-point operation** in the WAL. These entry-point markers are essential for multi-threaded replay — they tell the replay system which operations were external inputs that need to be re-injected during replay.

#### Excluding the Self-Caller's `main()`: `--no-wal-incoming-cli`

By default, the self-caller's `main()` invocation is also recorded as an entry-point operation in the WAL. For RPC services where `main()` contains only service setup code (e.g., starting an RPC listener, initializing configuration), this entry point is unnecessary — `main()` will be called naturally by the replay system via `SelfBootstrapInvoker` and does not need to be re-injected.

Use `--no-wal-incoming-cli` to exclude the `main()` invocation from the WAL:

```bash
pal run --wal file:/tmp/service-wal --no-wal-incoming-cli \
  --json-rpc auto --rpc-threads 4 \
  -cp target/classes com.example.ServiceMain
```

This is recommended when `main()` is purely setup code. Omitting it keeps the WAL focused on the actual RPC entry points and avoids potential complications during multi-threaded replay where the self-caller's entry-point span could interfere with cursor alignment.

**Recommended for RPC services:** When using `--wal-incoming-rpc` with multi-threaded RPC services (e.g., `--rpc-threads 4`), you should also add `--no-wal-incoming-cli`. Without it, the self-caller's `main()` entry point is recorded in the WAL, but during replay the self-caller thread does not use a `ReplayInputInjector` (its `main()` is called naturally by `SelfBootstrapInvoker`). This mismatch between the WAL data and the replay mechanism can cause cursor misalignment during multi-threaded replay.

**Note:** `--no-wal-incoming-cli` is **not** automatically enabled when `--wal-incoming-rpc` is specified. This is intentional: in some applications, `main()` contains business logic that should be replayed as an injected entry point rather than just running naturally. Add `--no-wal-incoming-cli` explicitly when you know that `main()` is purely setup code and does not contain injectable logic.

## Replaying from a WAL

### Basic Replay

```bash
pal replay --wal file:/tmp/my-wal \
  -cp target/classes com.example.App arg1 arg2
```

If the application produces the same operations with the same return values, the exit code is `0` and no divergences are reported.

### Slow-Motion Replay

For UI applications (JavaFX, Swing), operations can happen too fast to observe during replay. Use the `--delay` option to add a pause before each entry-point injection:

```bash
# 2-second delay between entry points (good for visual debugging)
pal replay --wal file:/tmp/fx-wal --fx-thread --delay 2000 \
  -jar target/my-javafx-app.jar
```

The delay is specified in milliseconds. Use larger values (2000-5000ms) when you want to observe each UI state change, smaller values (200-500ms) for faster replay that's still visible. A value of `0` (the default) disables the delay.

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

For a multi-threaded application with RPC input, the output shows multiple threads and entry-point operations:

```
WAL Index Summary
  Entries:     320
  Operations:  160
  Completions: 160
  Pairs:       160
  Threads:     [main, rpc-worker-1, rpc-worker-2]
  Issues:      0
```

Entry-point operations (marked in the WAL during recording with `--wal-incoming-rpc`) indicate where external input arrived. During replay, these entry points are re-injected by the `ReplayInputInjector`.

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

## Multi-Threaded Replay

Replay supports applications that receive input on multiple threads — RPC services, web applications, Swing GUIs, and timer-based applications. During recording, the WAL captures operations from all threads. During replay, the self-caller thread (running `main()`) is application-driven as before, while input threads are **WAL-driven**: entry-point operations are injected from the WAL into the runtime.

### How It Works

Replay uses a hybrid model:

- **Self-caller thread** (the thread running `main()`): The application runs naturally. Operations are matched against the WAL cursor and verified, exactly as in single-threaded replay.
- **Input threads** (RPC workers, UI threads, HTTP handlers): A `ReplayInputInjector` reads entry-point operations from the WAL and dispatches them into the system. Once injected, the method body runs through woven code, producing nested operations that are matched against the WAL cursor — the same mechanism as the self-caller thread.

```
Self-caller thread:
  main() → dispatch() → WAL cursor match → execute → verify

Input thread (RPC, UI, etc.):
  ReplayInputInjector reads WAL → injects method call → dispatch() → WAL cursor match → verify
```

Cross-thread ordering is maintained by a `ReplayGate` that ensures input injection threads do not run ahead of the self-caller thread. The gate uses WAL offsets: after each operation completes on the self-caller thread, the gate advances, allowing input injection threads whose entry points follow in the WAL to proceed.

### Prerequisite: `--wal-incoming-rpc` During Recording

Multi-threaded replay requires that incoming RPC calls were recorded as entry-point operations in the WAL. This is the default behavior when a WAL is configured, but if you have previously disabled it, ensure it is re-enabled:

```bash
# Record with RPC input captured (--wal-incoming-rpc is on by default)
pal run --wal file:/tmp/service-wal \
  --json-rpc auto --rpc-threads 4 \
  -cp target/classes com.example.ServiceMain
```

Without `--wal-incoming-rpc`, the WAL will not contain entry-point markers for input threads, and replay will only cover the self-caller thread.

### Example: Record and Replay an RPC Service

```bash
# 1. Start the service and record a WAL
pal run --wal file:/tmp/service-wal \
  --json-rpc auto --rpc-threads 3 \
  -cp target/classes com.example.ServiceMain

# 2. Send requests to the service (from another terminal)
pal call -p ws://localhost:9001 com.example.Service processOrder "order-1"
pal call -p ws://localhost:9001 com.example.Service processOrder "order-2"
pal call -p ws://localhost:9001 com.example.Service processOrder "order-3"

# 3. Stop the service (Ctrl-C or let it complete)

# 4. Inspect the WAL
pal wal-index file:/tmp/service-wal
# Threads:     [main, rpc-worker-1, rpc-worker-2, rpc-worker-3]

# 5. Replay — the replay system injects the recorded RPC calls
pal replay --wal file:/tmp/service-wal \
  -cp target/classes com.example.ServiceMain
```

During step 5, the replay system:

1. Runs `main()` on the self-caller thread (sets up the service)
2. Spawns a `ReplayInputInjector` thread for each recorded input thread (`rpc-worker-1`, `rpc-worker-2`, etc.)
3. Each injector re-dispatches the entry-point operations (the `processOrder` calls) from the WAL
4. Nested operations within each injected call are matched against the WAL cursor and verified

### `--threading` Flag

The `--threading` option controls cross-thread ordering during multi-threaded replay:

| Value | Behavior |
|-------|----------|
| `ordered` (default) | Entry-point injection follows WAL-offset ordering. Input injection threads wait for the self-caller thread to reach the appropriate point before injecting each entry point. Preserves the recorded execution order. |
| `unordered` | Entry-point injection runs without ordering constraints. Faster, but the execution order may differ from the recording. Use when order between threads does not matter (e.g., independent request handlers). |

```bash
# Default: ordered replay (preserves recorded execution order)
pal replay --wal file:/tmp/service-wal \
  -cp target/classes com.example.ServiceMain

# Unordered: faster, but cross-thread order may differ
pal replay --wal file:/tmp/service-wal --threading unordered \
  -cp target/classes com.example.ServiceMain
```

### Supported Scenarios

| Scenario | Input Thread | What Gets Injected |
|----------|-------------|---------------------|
| PAL RPC service | `rpc-worker-N` | Incoming ZMQ/JSON-RPC method calls |
| Web app | `http-worker-N` | HTTP request handler invocations |
| Swing app | `AWT-EventQueue-0` | UI event handler calls |
| Timer-based | `scheduler-N` | Scheduled task invocations |
| JavaFX app | `JavaFX Application Thread` | Button handlers, event callbacks |

For all scenarios, the prerequisite is the same: the entry-point operations must be captured in the WAL during recording. For PAL RPC services, `--wal-incoming-rpc` handles this automatically. For web apps, Swing apps, and timer-based applications, the framework's request/event dispatch must go through PAL-woven code.

### JavaFX Applications

JavaFX applications require the `--fx-thread` flag during both recording and replay. This ensures that UI event handlers (button clicks, callbacks, etc.) are executed on the real JavaFX Application Thread rather than a PAL-managed thread.

**Recording a JavaFX application:**

```bash
pal run --wal file:/tmp/fx-wal --fx-thread \
  -jar target/my-javafx-app.jar
```

**Replaying a JavaFX application:**

```bash
pal replay --wal file:/tmp/fx-wal --fx-thread \
  -jar target/my-javafx-app.jar
```

Without `--fx-thread`, UI interactions recorded on the JavaFX Application Thread will not be replayed correctly — the replay system would inject them on a PAL-managed thread, which cannot interact with the JavaFX scene graph.

**How it works:**

1. During recording, operations on the JavaFX Application Thread are marked with `threadAffinity = "fx-thread"` in the WAL.
2. During replay, the `ThreadAffinityDispatcher` routes these entry points to the real JavaFX thread via `Platform.runLater()`.
3. The UI event handlers execute on the real FX thread, producing the same nested operations as during recording.

**Lambda class name normalization:**

JavaFX applications heavily use lambdas for event handlers. Lambda class names are non-deterministic across JVM runs (e.g., `DashboardController$Lambda$653/0x00007fb994397710` in one run vs `DashboardController$Lambda$562/0x00007fbd08449670` in another). PAL automatically normalizes these during signature matching by stripping the `$N/address` suffix, so that `Foo$Lambda` matches regardless of the specific lambda index or memory address.

**Non-public access:**

Replay injections can access private fields and methods because they re-execute operations that originally ran inside the JVM with full access. RPC access control (handled by RPC policy) does not apply to replay injections.

## Side-Effect Shielding (Replay Policy)

By default, every operation is re-executed during replay (`RE_EXECUTE`). This works well for pure computations, but fails when the recorded execution interacted with external resources — databases, network services, the filesystem, `System.currentTimeMillis()`, `Math.random()`, etc. These resources may be unavailable, or they may produce different results during replay.

**Side-effect shielding** solves this: instead of re-executing an operation, the replay system returns the value that was recorded in the WAL. The operation is **stubbed** — skipped entirely, along with all nested operations within its span.

### Replay Actions

Each operation during replay is assigned one of five actions:

| Action | Behavior |
|--------|----------|
| `RE_EXECUTE` | Execute the operation and verify the return value against the WAL (default) |
| `RE_EXECUTE_UNCHECKED` | Execute without verification |
| `STUB_FROM_WAL` | Return the WAL-recorded value without executing |
| `STUB_FROM_WAL_VERIFIED` | Return the WAL value, but also execute and compare (for validation) |
| `STUB_WITH_SIDE_EFFECTS` | Return the WAL-recorded value and replay any field mutations from within the span |

### Entry Points vs Nested Operations

Understanding when replay actions apply is critical:

**Entry point injection** (operations injected by `ReplayInputInjector` via `dispatchIncoming`):
- Arguments **always** come from the WAL message — this is how injection works
- The method body **always** executes with those WAL arguments
- RE_EXECUTE vs STUB does not apply to the entry point itself

**Nested operations** (operations inside entry points, handled via `dispatchReplay`):
- Arguments come from **live execution state** (`pjp.getArgs()`)
- This is where RE_EXECUTE vs STUB makes a difference

| Aspect | RE_EXECUTE | STUB_FROM_WAL |
|--------|------------|---------------|
| **Arguments** | Live (from current execution) | Ignored |
| **Execution** | Yes, runs the code | No, skipped |
| **Return value** | Live result | WAL-recorded value |
| **Divergence** | Detected if result ≠ WAL | Never (uses WAL value) |

**Example flow:**

```
Entry point: submitTransaction("100", "Starbucks")  ← WAL args (always)
  │
  └─ Nested: createTransaction(100.0, "Starbucks")  ← live args
       │
       └─ Nested: System.currentTimeMillis()
            │
            ├─ RE_EXECUTE: executes → returns 1699999500000 (current time)
            │              WAL expected 1699999200000 → VALUE_MISMATCH
            │
            └─ STUB_FROM_WAL: skips → returns 1699999200000 (WAL value)
                              No divergence
```

In this example, `submitTransaction` is injected with its recorded arguments (`"100"`, `"Starbucks"`), so those values flow correctly into the method body. But `System.currentTimeMillis()` is a nested call that executes during replay — with RE_EXECUTE it returns the current time (causing a divergence), while with STUB_FROM_WAL it returns the recorded time.

### When to Use Each Action

**`STUB_FROM_WAL`** — Use for operations that are pure functions of external state: time, random numbers, network reads, console output. These operations have no side effects visible to the caller beyond their return value.

**`STUB_WITH_SIDE_EFFECTS`** — Use for operations that mutate objects visible to the caller. For example, a method like `enricher.enrich(order)` that modifies the `order` object's fields while also contacting a database. Stubbing with side effects returns the WAL value and replays the field mutations, so the caller sees the same state without the database call.

**`RE_EXECUTE`** — Use for deterministic pure computations (the default). The operation runs normally and the return value is verified against the WAL.

### Quick Start: `--shield-io`

The simplest way to enable side-effect shielding is the `--shield-io` flag, which activates built-in rules for common non-deterministic operations:

```bash
pal replay --wal file:/tmp/my-wal --shield-io \
  -cp target/classes com.example.App arg1 arg2
```

This stubs time operations (`System.currentTimeMillis()`, `System.nanoTime()`, and `java.time.*.now()` methods like `LocalTime.now()`, `Instant.now()`, etc.), random generators (`Math.random()`, `Random.**`, `ThreadLocalRandom.**`), I/O streams (`java.io` readers/writers), network operations (`java.net.**`), and `DriverManager.getConnection()` — all from WAL-recorded values. Everything else is re-executed normally.

### JavaFX Applications: `--shield-fx`

For JavaFX applications, use `--shield-fx` to stub wall-clock-dependent animation operations:

```bash
pal replay --wal file:/tmp/my-wal --shield-io --shield-fx --fx-thread \
  -jar target/my-javafx-app.jar
```

This stubs `Animation.setOnFinished()` (and similar callback setters) and `AnimationTimer.start/stop`. Animations still run for visual effects, but their completion callbacks are prevented from firing, avoiding spurious "extra operation" divergences after the WAL cursor is exhausted.

### Replay Policy Configuration (YAML)

For fine-grained control, create a YAML policy file:

```yaml
defaultAction: RE_EXECUTE

rules:
  - class: "java.lang.System"
    method: "currentTimeMillis"
    action: STUB_FROM_WAL

  - class: "java.lang.System"
    method: "nanoTime"
    action: STUB_FROM_WAL

  - class: "java.time.LocalTime"
    method: "now"
    action: STUB_FROM_WAL

  - class: "java.lang.Math"
    method: "random"
    action: STUB_FROM_WAL

  - class: "com.example.ExternalService"
    method: "**"
    action: STUB_WITH_SIDE_EFFECTS

  - class: "java.io.**"
    method: "**"
    action: STUB_FROM_WAL
```

Apply it with `--policy`:

```bash
pal replay --wal file:/tmp/my-wal --policy policy.yaml \
  -cp target/classes com.example.App
```

**Pattern syntax:** Class and method patterns use Ant-style matching:

- `*` matches any single path segment
- `**` matches zero or more segments
- `?` matches a single character

Examples: `java.io.**` matches all classes under `java.io`, `com.example.Service` matches exactly that class, `process*` matches `processOrder`, `processPayment`, etc.

**Rule evaluation:** Rules are evaluated in order; the first matching rule wins. If no rule matches, the `defaultAction` is used.

### CLI Pattern Flags

For quick one-off configurations without a YAML file:

```bash
# Stub specific classes
pal replay --wal file:/tmp/my-wal \
  --stub "java.lang.System.currentTimeMillis,java.lang.Math.random" \
  -cp target/classes com.example.App

# Re-execute specific classes, stub everything else
pal replay --wal file:/tmp/my-wal \
  --re-execute "com.example.**" --stub-all-else \
  -cp target/classes com.example.App

# Combine --shield-io with explicit re-execute overrides
pal replay --wal file:/tmp/my-wal --shield-io \
  --re-execute "com.example.TimeService.**" \
  -cp target/classes com.example.App

# JavaFX app: shield I/O and animations
pal replay --wal file:/tmp/my-wal --shield-io --shield-fx --fx-thread \
  -jar target/my-javafx-app.jar
```

**Flag priority** (highest to lowest):

1. `--re-execute` patterns
2. `--stub` patterns
3. `--shield-io` built-in rules
4. `--shield-fx` built-in rules
5. `--policy` YAML file rules

### Phantom Object Cascading

When a constructor or object-returning method is stubbed, the returned object may not be reconstructable from the WAL (e.g., a `java.sql.Connection`). In this case, the object becomes a **phantom** — a placeholder that exists only in the WAL's reference space.

Any subsequent operation on a phantom object is automatically stubbed, regardless of the replay policy. This cascading behavior means you only need to stub the root operation (e.g., `DriverManager.getConnection()`), and all operations on the resulting connection, prepared statements, and result sets are automatically handled.

```bash
# Stub only the connection factory — everything downstream cascades
pal replay --wal file:/tmp/my-wal \
  --stub "java.sql.DriverManager.getConnection" \
  -cp target/classes com.example.App
```

### Unsafe Stub Warnings

The replay system analyzes the WAL at startup to detect **unsafe stubs** — operations that would be stubbed but contain field mutations visible outside the span. For example, stubbing a method that modifies a passed-in object's fields would cause the caller to see stale state.

When unsafe stubs are detected, the replay exits with an error:

```
WARNING: Stubbing com.example.Enricher.enrich() at offset 42 is unsafe.
  Span contains PUT_FIELD order.enriched (ref 12345) referenced at offset 55 (outside span).
  Consider: RE_EXECUTE or STUB_WITH_SIDE_EFFECTS
```

**Resolution options:**

1. Change the action to `RE_EXECUTE` (re-execute the operation normally)
2. Change the action to `STUB_WITH_SIDE_EFFECTS` (stub but replay field mutations)
3. Use `--force-stub` to proceed with acknowledged risk

```bash
# Force replay despite unsafe stub warnings
pal replay --wal file:/tmp/my-wal --policy policy.yaml --force-stub \
  -cp target/classes com.example.App
```

### Common Policy Configurations

#### Replay with I/O Shielding (Most Common)

Stub all I/O and non-deterministic operations, re-execute application logic:

```bash
pal replay --wal file:/tmp/my-wal --shield-io \
  -cp target/classes com.example.App
```

#### Replay with Database Shielding

Stub database operations while re-executing business logic:

```yaml
# db-shield.yaml
defaultAction: RE_EXECUTE

rules:
  - class: "java.sql.DriverManager"
    method: "getConnection"
    action: STUB_FROM_WAL

  - class: "com.example.repository.**"
    method: "**"
    action: STUB_WITH_SIDE_EFFECTS
```

```bash
pal replay --wal file:/tmp/my-wal --policy db-shield.yaml \
  -cp target/classes com.example.App
```

#### Minimal Re-Execution (Stub Everything Else)

Only re-execute your application code, stub all library and framework calls:

```bash
pal replay --wal file:/tmp/my-wal \
  --re-execute "com.example.**" --stub-all-else \
  -cp target/classes com.example.App
```

#### Validation Mode (Stub + Verify)

Use `STUB_FROM_WAL_VERIFIED` to stub operations but also execute them in the background and compare results. Useful for verifying that a stub is safe before committing to it:

```yaml
# validate-stubs.yaml
defaultAction: RE_EXECUTE

rules:
  - class: "java.lang.System"
    method: "currentTimeMillis"
    action: STUB_FROM_WAL_VERIFIED

  - class: "com.example.ExternalService"
    method: "**"
    action: STUB_FROM_WAL_VERIFIED
```

## Current Limitations

- **Same code version expected**: Replay works best when the recorded and replayed code are identical or have small, targeted changes. Large structural changes will produce many operation-mismatch divergences.
- **No WAL output during replay**: Replay is read-only. A new WAL is not written during replay. This will be addressed when replay is integrated as a layer within the normal dispatch path.
- **Complex object reconstruction**: Stubbed return values are reconstructed from the WAL. Primitives, strings, and JSON-serializable objects are handled. Complex objects (e.g., database connections) become phantoms — their operations are auto-stubbed via phantom cascading.

## Further Reading

- [CLI Reference](../cli-reference.md) — Complete option documentation for `pal replay` and `pal wal-index`
- [Local Development Guide](local-development.md) — Recording WALs during development
- [Log Backends](../concepts/logs.md) — Chronicle Queue vs Kafka for WAL storage
