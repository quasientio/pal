# Recording Scope

Recording scope controls which operations are written to the WAL (Write-Ahead Log) and published via PUB. By default, PAL records every quantized operation — method calls, constructors, and field access — to the WAL. For applications with heavy use of JDK and library internals (`String.split()`, `HashMap.put()`, `Integer.valueOf()`, etc.), this produces large WALs where the vast majority of entries are noise.

Recording scope lets you filter the WAL down to what matters: your application code, plus any I/O boundaries you want to capture.

## What Recording Scope Is (and Isn't)

Recording scope filters what gets **written to the WAL and published via PUB**. It does not affect:

- **Quantization (AspectJ weaving)**: All classes are still woven at build time. The scope filter operates at runtime, inside the dispatch path.
- **Intercepts**: Intercepts fire regardless of recording scope. An intercept registered for `java.sql.DriverManager.getConnection` will still fire even if that operation is out of scope. Intercepts and recording scope are **orthogonal**.
- **Execution**: All operations still execute normally. Out-of-scope operations simply skip the WAL write and PUB publish steps.
- **RPC**: Remote procedure calls are unaffected. Scope controls persistence and publication, not execution.

Think of recording scope as a filter on the output side: everything runs, but only matching operations are persisted.

## Why Use Recording Scope

**Reduce WAL size**: Recording only your application code can reduce WAL size by 90% or more.

**Faster replay**: Smaller WALs mean faster replay indexing and fewer entries to match during deterministic replay.

**Focused debugging**: When inspecting a WAL with `pal log print` or `pal log index`, seeing only your application's operations makes it easier to understand execution flow.

**Targeted I/O capture**: Record your application code plus I/O boundaries (JDBC, HTTP, filesystem) to capture exactly what crossed system boundaries, without the noise of internal JDK operations.

## Quick Start

### Record Only Application Code

```bash
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-default skip \
  -cp app.jar com.example.Main
```

This records only operations on classes under `com.mycompany` and skips everything else (JDK, libraries, frameworks).

### Record Everything Except Noisy Internals

```bash
pal run --wal file:/tmp/my-wal \
  --scope-exclude "java.lang.String.**" \
  --scope-exclude "java.util.HashMap.**" \
  -cp app.jar com.example.Main
```

This records all operations except those on `String` and `HashMap`.

### Record Application Code + I/O Boundaries

```bash
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main
```

This records your application code plus common I/O operations (JDBC, HTTP, file I/O, time, random).

## CLI Flags

Recording scope is configured via five CLI flags on `pal run`. All flags are also available on `pal replay` (replay must use the same scope as recording — see [Replay Requirement](#replay-requirement)).

| Flag | Description |
|------|-------------|
| `--scope <patterns>` | Ant-style class patterns for operations to RECORD (repeatable, comma-separated) |
| `--scope-exclude <patterns>` | Ant-style class patterns for operations to SKIP (repeatable, comma-separated) |
| `--scope-io` | Include built-in I/O boundary rules (JDBC, HTTP, file I/O, time, random) |
| `--scope-policy <path>` | Path to a YAML recording scope policy file |
| `--scope-default <record\|skip>` | Default action when no rule matches (default: `record`) |

### Pattern Syntax

Patterns use Ant-style matching with `.` as the separator:

| Pattern | Matches |
|---------|---------|
| `com.example.Calculator` | Exact class name |
| `com.example.*` | Classes directly in `com.example` package |
| `com.example.**` | Classes in `com.example` and all sub-packages |

Matching is **case-insensitive**.

`--scope` patterns create RECORD rules (include these operations). `--scope-exclude` patterns create SKIP rules (exclude these operations). When both are specified, exclude rules have higher priority.

### Default Action Inference

When `--scope-default` is not explicitly set, the default action is inferred:

| Flags Given | Inferred Default |
|-------------|-----------------|
| Only `--scope` patterns | `skip` (record only what you specified) |
| Only `--scope-exclude` patterns | `record` (record everything except exclusions) |
| Both `--scope` and `--scope-exclude` | `skip` |
| Only `--scope-io` | `skip` |
| No scope flags | No filtering (all operations recorded) |

You can always override inference with an explicit `--scope-default`.

## Rule Evaluation

Rules are evaluated in **first-match-wins** order:

1. CLI `--scope-exclude` patterns (SKIP rules — highest priority)
2. CLI `--scope` patterns (RECORD rules)
3. `--scope-io` preset rules (RECORD rules)
4. YAML policy file rules (lowest priority)

If no rule matches, the default action applies.

This ordering ensures explicit exclusions always win over inclusions, which is the same model used by [RPC Policy](rpc-policy.md).

## YAML Policy Format

For fine-grained control — including member-level patterns, field operation filtering, and mixed include/exclude rules — use a YAML policy file.

### Schema

```yaml
defaultAction: SKIP           # or RECORD — action when no rule matches

rules:
  - class: "com.mycompany.**"   # Ant-style class pattern (required)
    member: "**"                 # Ant-style member pattern (optional, defaults to "**")
    action: RECORD               # RECORD or SKIP (required)
    categories:                  # Optional list of member categories
      - METHOD
      - CONSTRUCTOR
```

**Case sensitivity:** YAML action values (`defaultAction`, `action`) must be uppercase (`RECORD`, `SKIP`). The CLI flag values (`--scope-default record|skip`) are case-insensitive — they're normalized internally.

### Rule Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `class` | Yes | -- | Ant-style class pattern |
| `member` | No | `**` (all members) | Ant-style member name pattern |
| `action` | Yes | -- | `RECORD` or `SKIP` |
| `categories` | No | all categories | List of member categories to match |

### Member Categories

| Category | Description |
|----------|-------------|
| `METHOD` | Instance method invocation |
| `STATIC_METHOD` | Static method invocation |
| `CONSTRUCTOR` | Object construction |
| `FIELD_GET` | Field or static field read |
| `FIELD_SET` | Field or static field write |

When `categories` is omitted, the rule matches all operation types. When specified, the rule only matches operations of the listed categories.

### Member Patterns

The `member` field matches the operation name:

- For methods: the method name (e.g., `process`, `get*`, `**`)
- For constructors: `new`
- For fields: the field name (e.g., `counter`, `*Name`, `**`)

### Example Policies

#### Application Code Only

```yaml
defaultAction: SKIP

rules:
  - class: "com.mycompany.**"
    action: RECORD
```

#### Exclude Noisy Internals

```yaml
defaultAction: RECORD

rules:
  - class: "java.lang.String"
    action: SKIP

  - class: "java.util.HashMap"
    action: SKIP

  - class: "java.util.ArrayList"
    action: SKIP
```

#### Application Code with I/O Boundaries

```yaml
defaultAction: SKIP

rules:
  # Record all application code
  - class: "com.mycompany.**"
    action: RECORD

  # But exclude internal utilities
  - class: "com.mycompany.internal.util.**"
    action: SKIP

  # Record JDBC operations
  - class: "java.sql.DriverManager"
    member: "getConnection"
    action: RECORD

  - class: "java.sql.Connection"
    action: RECORD
```

#### Field Operation Targeting

```yaml
defaultAction: SKIP

rules:
  # Record all operations on domain objects
  - class: "com.mycompany.model.**"
    action: RECORD

  # Record field writes on order objects (but not reads)
  - class: "com.mycompany.model.Order"
    categories: [FIELD_SET]
    action: RECORD

  # Skip all field reads on JDK types
  - class: "java.**"
    categories: [FIELD_GET]
    action: SKIP

  # Skip all constructors for collection types
  - class: "java.util.**"
    member: "new"
    categories: [CONSTRUCTOR]
    action: SKIP
```

## Field Operations

Field reads and writes are a significant source of WAL entries — every `getfield` and `getstatic` in woven code produces an entry — so filtering them can substantially reduce WAL size. CLI flags (`--scope`, `--scope-exclude`) match all operation types including field access; to filter fields independently of methods and constructors, use a YAML policy with `categories: [FIELD_GET]` or `[FIELD_SET]` (see [Field Operation Targeting](#field-operation-targeting)).

## Built-In I/O Boundary Rules (`--scope-io`)

The `--scope-io` flag adds RECORD rules for common I/O boundary operations. These are operations that cross system boundaries — the kind of operations you typically want in a WAL even when filtering out JDK internals.

| Category | Operations |
|----------|------------|
| **JDBC** | `java.sql.DriverManager.getConnection`, `java.sql.Connection.**`, `java.sql.Statement.**`, `java.sql.PreparedStatement.**`, `java.sql.CallableStatement.**`, `java.sql.ResultSet.**` |
| **HTTP Client** | `java.net.http.HttpClient.**`, `java.net.http.HttpRequest.**`, `java.net.http.HttpResponse.**`, `java.net.URL.openConnection`, `java.net.HttpURLConnection.**` |
| **File I/O** | `java.io.FileInputStream.**`, `java.io.FileOutputStream.**`, `java.io.FileReader.**`, `java.io.FileWriter.**`, `java.io.RandomAccessFile.**`, `java.nio.file.Files.**`, `java.nio.channels.FileChannel.**` |
| **Network I/O** | `java.net.Socket.**`, `java.net.ServerSocket.**`, `java.nio.channels.SocketChannel.**`, `java.nio.channels.ServerSocketChannel.**` |
| **Time** | `java.lang.System.currentTimeMillis`, `java.lang.System.nanoTime`, `java.time.Clock.**`, `java.time.Instant.now`, `java.time.LocalDateTime.now`, `java.time.LocalDate.now`, `java.time.LocalTime.now`, `java.time.ZonedDateTime.now`, `java.time.OffsetDateTime.now` |
| **Random** | `java.lang.Math.random`, `java.util.Random.**`, `java.util.concurrent.ThreadLocalRandom.**` |
| **Process/Runtime** | `java.lang.ProcessBuilder.**`, `java.lang.Runtime.exec` |
| **System** | `java.lang.System.getProperty`, `java.lang.System.getenv` |

## Relationship with `--shield-io` (Replay)

`--scope-io` and `--shield-io` are complementary flags that work at different stages:

| Flag | When | What It Does |
|------|------|-------------|
| `--scope-io` | Recording (`pal run`) | **Records** I/O boundary operations to WAL |
| `--shield-io` | Replay (`pal replay`) | **Stubs** I/O operations from WAL values (returns recorded values without executing) |

A typical workflow:

```bash
# Step 1: Record with scope — capture app code + I/O boundaries
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main

# Step 2: Replay with shielding — re-execute app code, stub I/O from WAL
pal replay --wal file:/tmp/my-wal --shield-io \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main
```

In this workflow:

1. During recording, `--scope-io` ensures I/O operations are captured in the WAL (along with application code).
2. During replay, `--shield-io` stubs those I/O operations — returning the WAL-recorded values without actually hitting the database, network, or filesystem.
3. The `--scope` flags must be the same for both recording and replay (see below).

The specific patterns covered by `--scope-io` and `--shield-io` may differ slightly — `--scope-io` includes I/O boundaries the user wants to capture, while `--shield-io` stubs non-deterministic operations that shouldn't be re-executed. Overlap is expected and harmless.

## Replay Requirement

**Replay must use the same `--scope` flags as recording.** This is critical for correctness. Recording flags are not stored as WAL metadata, so `pal replay` cannot infer them — passing the matching flags is your responsibility.

When you record with `--scope "com.mycompany.**" --scope-default skip`, operations outside that scope produce no WAL entries. During replay, the replay system needs to know which operations to match against WAL entries and which to execute directly (without WAL matching). If the scope differs between recording and replay:

- **Missing scope during replay**: Out-of-scope operations will try to match against WAL entries that don't exist, causing `EXTRA_OPERATION` divergences.
- **Different scope during replay**: The WAL cursor will become misaligned, producing cascading divergences.

Always pass the same scope flags to both `pal run` and `pal replay`:

```bash
# Record
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main

# Replay — same scope flags
pal replay --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main
```

## Usage Examples

The Quick Start section above covers the most common single-flag scenarios. The examples below cover cases that go beyond a single CLI flag.

### Example 1: YAML Policy for Complex Filtering

When CLI flags aren't granular enough, use a YAML policy:

```bash
pal run --wal file:/tmp/my-wal \
  --scope-policy scope-policy.yaml \
  -cp app.jar com.example.Main
```

With `scope-policy.yaml`:

```yaml
defaultAction: SKIP

rules:
  # Record all application code
  - class: "com.mycompany.**"
    action: RECORD

  # But exclude the chatty logging wrapper
  - class: "com.mycompany.util.LogWrapper"
    action: SKIP

  # Record JDBC boundary operations
  - class: "java.sql.**"
    action: RECORD

  # Record field writes on domain models for audit
  - class: "com.mycompany.model.**"
    categories: [FIELD_SET]
    action: RECORD
```

### Example 2: Replay with Matching Scope

```bash
# Record
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main arg1 arg2

# Replay with same scope — zero divergences expected
pal replay --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main arg1 arg2
```

## Further Reading

- [CLI Reference](../cli-reference.md) — Complete `pal run` and `pal replay` option documentation
- [Deterministic Replay](deterministic-replay.md) — Recording WALs, replaying, and understanding divergences
- [RPC Policy](rpc-policy.md) — Similar first-match-wins pattern matching for access control
- [Log Backends](logs.md) — Chronicle Queue vs Kafka for WAL storage
