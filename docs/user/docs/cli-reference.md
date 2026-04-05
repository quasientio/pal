# CLI Reference

PAL provides a command-line interface for managing peers, logs, and remote procedure calls. For a hands-on introduction to `pal run`, see the [Getting Started](getting-started.md) guide. This reference covers all CLI subcommands, starting with `pal run` WAL options.

## Overview

The PAL CLI uses an entity-operation command structure inspired by Docker:

```bash
pal <entity> <operation> [OPTIONS] [ARGUMENTS]
```

**Management Commands** (entity groups):

| Command | Description |
|---------|-------------|
| `pal peer <command>` | Manage peers |
| `pal log <command>` | Manage logs |
| `pal intercept <command>` | Manage intercepts |

**Commands**:

| Command | Description |
|---------|-------------|
| `pal run` | Run a new peer |
| `pal replay` | Deterministic WAL replay |
| `pal init` | Initialize a project for PAL |

**Shortcuts** (aliases for common operations):

| Shortcut | Equivalent |
|----------|------------|
| `pal peers` | `pal peer ls` |
| `pal logs` | `pal log ls` |
| `pal intercepts` | `pal intercept ls` |

### Global Options

- `-d, --dir <HOST:PORT>` - PAL directory URL (etcd endpoint, e.g., `localhost:2379`)
- `-h, --help` - Display help for the command

The directory URL can also be set via the `PAL_DIRECTORY` environment variable. Kafka servers can be set via the `PAL_KAFKA_SERVERS` environment variable.

Run `pal COMMAND --help` for more information on a command.

To get help on a subcommand, run `pal COMMAND SUBCOMMAND --help`. For example:

```bash
pal peer --help         # List all peer operations
pal peer ls --help      # Help for 'pal peer ls'
pal log print --help    # Help for 'pal log print'
```

## Registry Mode vs Direct Mode

PAL CLI commands support two modes of operation:

### Registry Mode (with PAL_DIRECTORY)

Uses the PAL directory (etcd) to look up resources by name or UUID. This is the standard mode when multiple peers and systems need to coordinate.

**Advantages**:

- Central service discovery
- Name-based lookup (no need to remember UUIDs or paths)
- Automatic resolution of log locations
- Shared visibility across distributed systems

**Usage**: Specify `-d/--dir` option or set `PAL_DIRECTORY` environment variable.

```bash
pal log print -d localhost:2379 my-log
```

### Direct Mode (without PAL_DIRECTORY)

Directly accesses Kafka logs or Chronicle logs without using the PAL directory. Useful for:

- Local development with Chronicle Queue
- Accessing logs without etcd infrastructure
- Scripts that know exact log locations
- Debugging and troubleshooting

**Direct access to logs**:

- **Chronicle logs**: Use `file:` prefix followed by path (absolute or relative)
- **Kafka logs**: Specify `-k` option with bootstrap servers

**Usage examples**:

```bash
# Chronicle log (local file)
pal log print file:/tmp/my-log

# Kafka log (specify servers)
pal log print -k localhost:29092 my-log
```

### Command Support

| Command | Registry Mode | Direct Mode | Notes |
|---------|--------------|-------------|-------|
| `pal peer ls` | ✓ | ✗ | Requires directory |
| `pal log ls` | ✓ | ✗ | Requires directory |
| `pal intercept ls` | ✓ | ✗ | Requires directory |
| `pal log print` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal peer print` | ✗ | ✓ | Direct peer connection |
| `pal peer call` | ✓ | ✓ | By name, UUID, or address |
| `pal log call` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal peer rm` | ✓ | ✗ | Requires directory |
| `pal log rm` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal replay` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal log index` | ✓ | ✓ | Both Chronicle and Kafka |

## pal run - WAL Options

The `pal run` command starts a new peer. The [Getting Started](getting-started.md) guide covers basic usage. This section documents the WAL (Write-Ahead Log) options that control which messages are written to the log.

### WAL Incoming Message Flags

By default, when a WAL is configured (`--wal`), PAL writes **all** messages to it: both locally-initiated operations (from the peer's own application code) and incoming operations (from RPC calls or CLI bootstrap). These flags let you control which incoming messages are written.

| Option | Default | Description |
|--------|---------|-------------|
| `--wal-incoming-rpc` / `--no-wal-incoming-rpc` | `true` | Write incoming RPC calls to WAL. Covers ZMQ-RPC and JSON-RPC (WebSocket) channels. Does **not** include messages arriving via source log replay (`LOG_RPC`). |
| `--wal-incoming-cli` / `--no-wal-incoming-cli` | `true` | Write incoming CLI bootstrap calls to WAL. Covers the `main()` invocation initiated by `SelfBootstrapInvoker` when a main class is specified on the command line. |
| `--wal-all-incoming-rpc` | `false` | Write **all** incoming RPC calls to WAL, including `LOG_RPC` (source log replay). Implies `--wal-incoming-rpc`. Has a built-in circularity guard: if the source log and WAL are the same log, this option is ignored to prevent infinite feedback loops. |

These flags only take effect when a WAL or TCP PUB destination is configured (i.e., `--wal` or `--tcp-pub` is specified). Without a destination, the flags are silently ignored.

### When to Use These Flags

**Default behavior (all enabled)** is correct for most use cases: the WAL captures a complete record of everything the peer did, regardless of how the operation was initiated.

Disable `--wal-incoming-rpc` when:

- You want the WAL to only contain locally-initiated operations
- The caller is already logging the RPC on its side

Disable `--wal-incoming-cli` when:

- You don't need the bootstrap `main()` call recorded in the WAL
- You want the WAL to start recording only after the application is initialized

Enable `--wal-all-incoming-rpc` when:

- You are consuming from one log (source) and writing to a different log (WAL)
- You want replayed messages to be re-published to the WAL for downstream consumers

### RPC Policy Flags

Control which operations remote callers can invoke via RPC. See [RPC Policy](concepts/rpc-policy.md) for full documentation.

| Option | Default | Description |
|--------|---------|-------------|
| `--rpc-policy <path>` | -- | Path to RPC access policy YAML file |
| `--rpc-policy-preset <names>` | -- | Comma-separated preset names to enable (e.g., `deny-unsafe,deny-jdk-internals`) |
| `--rpc-default-action <action>` | `DENY` | Default action when no rule matches: `ALLOW` or `DENY` |
| `--rpc-policy-watch-interval <ms>` | `2000` | Poll interval in milliseconds for watching the policy YAML file for changes. When a change is detected, the policy is reloaded automatically without restarting the peer. Set to `0` to disable file watching |

When no policy flags are specified, all RPC operations are denied by default. To allow RPC calls, configure a policy with explicit ALLOW rules or pass `--rpc-default-action ALLOW`. When a policy is configured, it gates every incoming RPC message before dispatch.

When a policy YAML file is specified via `--rpc-policy`, the peer automatically watches the file for changes and reloads the policy at runtime. See [RPC Policy — Hot Reloading](concepts/rpc-policy.md#hot-reloading) for details.

Available presets: `deny-unsafe`, `deny-jdk-internals`, `deny-classloading`, `deny-reflection`, `deny-serialization`, `deny-scripting`, `deny-pal-internals`. Note: `deny-pal-internals` is always enforced regardless of configuration and cannot be disabled.

```bash
# Block dangerous operations
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy-preset deny-unsafe,deny-jdk-internals \
  -cp app.jar com.example.Main

# Use a YAML policy file (with automatic hot reloading)
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy.yaml \
  -cp app.jar com.example.Main

# Deny by default, allow only via policy rules
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy.yaml --rpc-default-action DENY \
  -cp app.jar com.example.Main

# Custom poll interval (5 seconds)
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy.yaml --rpc-policy-watch-interval 5000 \
  -cp app.jar com.example.Main

# Disable file watching (load once at startup)
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy.yaml --rpc-policy-watch-interval 0 \
  -cp app.jar com.example.Main
```

### Recording Scope Flags

Control which operations are written to the WAL and published via PUB. By default, all quantized operations are recorded. Recording scope lets you filter the WAL to include only relevant operations (e.g., your application code), reducing WAL size and noise. See [Recording Scope](concepts/recording-scope.md) for full documentation.

| Option | Default | Description |
|--------|---------|-------------|
| `--scope <patterns>` | -- | Ant-style class patterns for operations to record (repeatable, comma-separated). Creates RECORD rules |
| `--scope-exclude <patterns>` | -- | Ant-style class patterns for operations to exclude from recording (repeatable, comma-separated). Creates SKIP rules. Takes priority over `--scope` |
| `--scope-io` | `false` | Include built-in I/O boundary rules: JDBC, HTTP, file I/O, network I/O, time, random, process, system properties. All added as RECORD rules |
| `--scope-policy <path>` | -- | Path to a YAML recording scope policy file for fine-grained control (class, member, categories, action). Lowest priority |
| `--scope-default <record\|skip>` | inferred | Default action when no rule matches. When omitted: inferred as `skip` if `--scope` or `--scope-io` is given, `record` if only `--scope-exclude` is given |

These flags only take effect when a WAL or TCP PUB destination is configured (i.e., `--wal` or `--tcp-pub` is specified). Without a destination, the flags are silently ignored.

Rules are evaluated in first-match-wins order: `--scope-exclude` rules (highest priority), then `--scope` rules, then `--scope-io` preset rules, then `--scope-policy` YAML rules, then the default action.

**Important**: When using recording scope with deterministic replay (`pal replay`), the same `--scope` flags must be passed to the replay command. See [Recording Scope — Replay Requirement](concepts/recording-scope.md#replay-requirement).

```bash
# Record only application code (skip everything else)
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-default skip \
  -cp app.jar com.example.Main

# Record everything except noisy JDK internals
pal run --wal file:/tmp/my-wal \
  --scope-exclude "java.lang.String.**" \
  --scope-exclude "java.util.HashMap.**" \
  -cp app.jar com.example.Main

# Record application code + I/O boundaries
pal run --wal file:/tmp/my-wal \
  --scope "com.mycompany.**" --scope-io --scope-default skip \
  -cp app.jar com.example.Main

# Use a YAML policy file for fine-grained control
pal run --wal file:/tmp/my-wal \
  --scope-policy scope-policy.yaml \
  -cp app.jar com.example.Main
```

### Examples

```bash
# Default: all incoming messages written to WAL
pal run -k localhost:29092 --wal my-wal --json-rpc auto -cp app.jar com.example.Main

# Disable WAL writes for incoming RPC (only locally-initiated operations logged)
pal run -k localhost:29092 --wal my-wal --no-wal-incoming-rpc --json-rpc auto \
  -cp app.jar com.example.Main

# Disable WAL writes for the CLI bootstrap main() call
pal run -k localhost:29092 --wal my-wal --no-wal-incoming-cli --json-rpc auto \
  -cp app.jar com.example.Main

# Consume from one Kafka topic, re-publish all messages (including replayed) to another
pal run -k localhost:29092 --source-log input-topic --wal output-topic \
  --wal-all-incoming-rpc -cp app.jar
```

---

## pal init - Initialize a Project for PAL

Scaffold a new project or add PAL weaving to an existing Maven or Gradle project. In interactive mode (default), a wizard guides you through the setup. In non-interactive mode (`-y`), all choices are specified via flags.

### Synopsis

```bash
pal init [OPTIONS] [DIRECTORY]
pal init                             # Interactive wizard in current directory
pal init my-project                  # Create new project in my-project/
pal init -y --group-id com.example   # Non-interactive mode with flags
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `DIRECTORY` | Target directory (default: current directory). If specified and the directory does not exist, it is created |

### Options

#### Project Identity

| Option | Description |
|--------|-------------|
| `--group-id <GROUP_ID>` | Maven/Gradle group ID (e.g., `com.example`) |
| `--artifact-id <ARTIFACT_ID>` | Maven/Gradle artifact ID (e.g., `my-app`) |
| `--version <VERSION>` | Project version (default: `1.0-SNAPSHOT`) |
| `--main-class <CLASS>` | Fully qualified main class name (e.g., `com.example.Main`) |
| `--package <PACKAGE>` | Java package name (inferred from group ID if omitted) |

#### Build and Mode

| Option | Description |
|--------|-------------|
| `--build-tool <maven\|gradle>` | Build tool selection (default: auto-detect from existing project, or `maven` for new projects) |
| `--mode <local\|distributed\|both>` | Deployment mode (default: `local`). `local` uses Chronicle Queue (no infrastructure needed), `distributed` uses etcd + Kafka, `both` generates configs for both |
| `-y, --non-interactive` | Skip interactive prompts, use defaults and flag values |

#### Feature Toggles

| Option | Default | Description |
|--------|---------|-------------|
| `--sample-app` / `--no-sample-app` | `true` | Generate sample application code |
| `--rpc-policy` / `--no-rpc-policy` | `false` | Generate RPC policy config |
| `--scope-policy` / `--no-scope-policy` | `false` | Generate recording scope config |
| `--logging-config` / `--no-logging-config` | `true` | Generate PAL runtime logging configuration (`peer-logging.xml`, `cli-logging.xml`) |
| `--intercept-bundle` / `--no-intercept-bundle` | `false` | Generate intercept bundle example |
| `--infra` / `--no-infra` | `false` | Generate Docker infrastructure files (etcd + Kafka) |

#### Safety

| Option | Description |
|--------|-------------|
| `--force` | Overwrite existing files without prompting |
| `--dry-run` | Preview changes without writing any files. Shows what would be generated or patched |

### Behavior

**New project:** When the target directory does not contain a `pom.xml` or `build.gradle`, `pal init` creates a complete project structure including the build file, source directories, sample code (if enabled), and configuration files.

**Existing project:** When a `pom.xml` or `build.gradle` is detected, `pal init` patches the existing build file to add PAL weaving. A backup is created (`pom.xml.backup` or `build.gradle.backup`) before modification. The patcher is idempotent — running it twice produces no duplicate elements.

**Dry run:** With `--dry-run`, no files are written to disk. Instead, the command shows what would be generated or patched, allowing you to review the changes before committing.

### Examples

#### New Maven Project

```bash
# Interactive wizard
pal init my-pal-app

# Non-interactive with defaults
pal init my-pal-app -y \
  --group-id com.example \
  --artifact-id my-pal-app \
  --main-class com.example.Main
```

#### New Gradle Project

```bash
# Interactive wizard with Gradle
pal init my-pal-app --build-tool gradle

# Non-interactive
pal init my-pal-app -y \
  --build-tool gradle \
  --group-id com.example \
  --artifact-id my-pal-app \
  --main-class com.example.Main
```

#### Existing Project

```bash
# Patch an existing Maven project in the current directory
cd my-existing-project
pal init

# Patch an existing Gradle project
pal init --build-tool gradle
```

#### Distributed Mode

```bash
# New project with etcd + Kafka infrastructure files
pal init my-distributed-app -y \
  --group-id com.example \
  --artifact-id my-distributed-app \
  --main-class com.example.Main \
  --mode distributed \
  --infra
```

#### Preview Changes (Dry Run)

```bash
# See what pal init would generate for a new project
pal init my-app --dry-run

# See what pal init would patch on an existing project
cd my-existing-project
pal init --dry-run
```

---

## pal peer ls - List Peers

List registered peers in the directory.

**Alias**: `pal peers`

### Synopsis

```bash
pal peer ls [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-l, --long` | Use long listing format with detailed information |
| `-c, --sort-by-ctime` | Sort by creation/uptime, newest first |
| `-r, --reverse` | Reverse the sorting order |
| `--no-trim` | Disable trimming of long field values |

### Examples

```bash
# List running peers
pal peer ls -d localhost:2379

# List peers with detailed information
pal peer ls -d localhost:2379 -l

# List peers sorted by uptime (newest first)
pal peer ls -d localhost:2379 -c

# Using the alias
pal peers -d localhost:2379
```

### Long Format Output

```
UUID                                 Name            ZMQ-RPC              JSON-RPC             PUB                  JMX                  Uptime
```

- UUID: Peer unique identifier
- Name: Peer name (if set; must be unique within the directory)
- ZMQ-RPC: Binary RPC endpoint (tcp://)
- JSON-RPC: JSON-RPC endpoint (ws://)
- PUB: Message publication endpoint (tcp://)
- JMX: JMX monitoring endpoint
- Uptime: Time since peer started (H:mm:ss format)

---

## pal log ls - List Logs

List registered logs in the directory.

**Alias**: `pal logs`

### Synopsis

```bash
pal log ls [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-l, --long` | Use long listing format with detailed information |
| `-S, --sort-by-size` | Sort logs by size, largest first |
| `-c, --sort-by-ctime` | Sort by creation time, newest first |
| `-r, --reverse` | Reverse the sorting order |
| `--no-trim` | Disable trimming of long field values |

### Examples

```bash
# List all logs
pal log ls -d localhost:2379

# List logs with detailed information
pal log ls -d localhost:2379 -l

# List logs sorted by size (largest first) in long format
pal log ls -d localhost:2379 -S -l

# List logs sorted by size (smallest first)
pal log ls -d localhost:2379 -S -r

# List logs sorted by creation time (newest first)
pal log ls -d localhost:2379 -c

# Using the alias
pal logs -d localhost:2379 -l
```

### Long Format Output

```
Name                 UUID                                 Size       Start    --> End      Created
```

- Name: Log name
- UUID: Log unique identifier
- Size: Total size in human-readable format (KB, MB, GB)
- Start: First available offset/index
- End: Last available offset/index
- Created: Creation timestamp (MMM dd HH:mm format)

---

## pal intercept ls - List Intercepts

List registered intercepts in the directory.

**Alias**: `pal intercepts`

### Synopsis

```bash
pal intercept ls [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-l, --long` | Use long listing format with detailed information |
| `-c, --sort-by-ctime` | Sort by creation time, newest first |
| `-r, --reverse` | Reverse the sorting order |
| `--no-trim` | Disable trimming of long field values |

### Examples

```bash
# List all registered intercepts
pal intercept ls -d localhost:2379

# List intercepts with detailed information
pal intercept ls -d localhost:2379 -l

# List intercepts sorted by creation time (newest first)
pal intercept ls -d localhost:2379 -c -l

# Using the alias
pal intercepts -d localhost:2379 -l
```

### Long Format Output

```
UUID                                 Peer                                 Type         Class                          Target                    Callback                       TTL      Created
```

- UUID: Intercept request unique identifier
- Peer: UUID of the peer that registered the intercept
- Type: Intercept type (BEFORE, AFTER, AROUND, BEFORE_ASYNC, AFTER_ASYNC)
- Class: Simple name of the intercepted class
- Target: Intercepted method signature or field operation (e.g., `add(int, int)` or `counter [GET]`)
- Callback: Simple callback class name and method (e.g., `Handler.onAdd`)
- TTL: Time-to-live in seconds (e.g., `300s`), or `-` if the intercept has no dedicated TTL
- Created: Creation timestamp (MMM dd HH:mm format)

### Notes (ls commands)

- Lists both Kafka and Chronicle logs
- Chronicle logs use `file:` prefix in the directory but are displayed without it
- Long format truncates long values with ".." to fit columns
- Logs must exist in their backing store (Kafka or Chronicle) to be displayed
- Intercepts are listed from the etcd directory; they exist as long as the owning peer's lease is active

---

## pal intercept apply - Apply Intercept Bundle

Apply intercepts from a YAML bundle file to the PAL directory. Reads a YAML bundle definition, resolves peers by name, and creates the intercepts. Supports a dry-run mode that shows what would be applied without making changes.

For an introduction to intercept bundles, see [Intercept Bundles](concepts/interception.md#intercept-bundles).

### Synopsis

```bash
pal intercept apply [OPTIONS] FILE
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `FILE` | Path to a YAML bundle file |

### Options

| Option | Description |
|--------|-------------|
| `--dry-run` | Show what would change without applying (equivalent to `pal intercept diff`) |
| `-q, --quiet` | Suppress per-intercept detail, print only summary line |

### YAML Bundle Format

```yaml
bundle: "fraud-check-v1"
defaults:
  peer: "fraud-checker"
  priority: 0
  ttl: 30s
  forceImmediate: false
  exceptionPolicy: PROPAGATE_CONTROLLED_ONLY
  checkedExceptionPolicy: WRAP

intercepts:
  - target: com.acme.payment.OrderService.placeOrder
    type: BEFORE
    callback:
      class: com.acme.fraud.FraudChecker
      method: verify
    params: [com.acme.payment.Order]
    priority: 10
    ttl: 15m

  - target: com.acme.payment.OrderService.refund
    type: AROUND
    callback:
      class: com.acme.fraud.FraudChecker
      method: wrapRefund

  - target: com.acme.payment.OrderService.status
    kind: field
    fieldOp: GET
    type: AFTER
    callback:
      class: com.acme.audit.FieldAuditor
      method: onFieldRead
```

**Required fields:**

- `bundle` - A unique name for this bundle
- `intercepts` - A list of intercept definitions, each with:
    - `target` - Fully qualified `ClassName.methodOrFieldName`
    - `type` - `BEFORE`, `AFTER`, `AROUND`, `BEFORE_ASYNC`, or `AFTER_ASYNC`
    - `callback.class` and `callback.method` - The callback handler

**Optional fields:**

- `defaults` - Bundle-level defaults inherited by all intercepts:
    - `peer` - Peer name (resolved via directory)
    - `priority` - Default priority (0 = normal)
    - `ttl` - Duration string (`30s`, `15m`, `1h`, `1d`)
    - `forceImmediate` - Skip in-flight tracking (`true`/`false`)
    - `exceptionPolicy` - `PROPAGATE_CONTROLLED_ONLY`, `PROPAGATE_ALL`, `SUPPRESS_ALL`
    - `checkedExceptionPolicy` - `WRAP`, `SUPPRESS`
- Per-intercept overrides: `peer`, `priority`, `ttl`, `forceImmediate`, `exceptionPolicy`, `checkedExceptionPolicy`
- `kind` - `method` (default) or `field`
- `fieldOp` - `GET` or `PUT` (required when `kind: field`)
- `params` - List of parameter types for overloaded method matching

### Examples

```bash
# Apply a bundle
pal intercept apply -d localhost:2379 fraud-check.yaml

# Preview what would change without applying
pal intercept apply -d localhost:2379 --dry-run fraud-check.yaml

# Apply quietly (summary only)
pal intercept apply -d localhost:2379 -q fraud-check.yaml
```

### Output

```
Applying bundle "fraud-check-v1" (3 intercepts)...
  + BEFORE com.acme.payment.OrderService.placeOrder -> created
  + AROUND com.acme.payment.OrderService.refund -> created
  + AFTER com.acme.payment.OrderService.status -> created
Applied: 3 created, 0 skipped, 0 failed
```

Re-applying the same bundle is idempotent --- existing intercepts are skipped:

```
Applied: 0 created, 3 skipped, 0 failed
```

---

## pal intercept rm - Remove Intercepts

Remove intercepts from the PAL directory by UUID, YAML file, bundle name, or peer.

### Synopsis

```bash
pal intercept rm [OPTIONS] [UUID...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `UUID` | One or more intercept UUIDs to remove (mutually exclusive with options below) |

### Options

| Option | Description |
|--------|-------------|
| `-f, --file FILE` | Remove all intercepts defined in a YAML bundle file |
| `--bundle NAME` | Remove all intercepts tracked in the named bundle's metadata |
| `--peer PEER` | Remove all intercepts for a peer (by name or UUID) |
| `-q, --quiet` | Suppress per-intercept detail |

Exactly one removal mode must be specified: positional UUIDs, `-f`, `--bundle`, or `--peer`.

### Examples

```bash
# Remove individual intercepts by UUID
pal intercept rm -d localhost:2379 abc12345-... def67890-...

# Remove all intercepts from a YAML bundle
pal intercept rm -d localhost:2379 -f fraud-check.yaml

# Remove all intercepts by bundle name (uses stored metadata)
pal intercept rm -d localhost:2379 --bundle fraud-check-v1

# Remove all intercepts for a peer
pal intercept rm -d localhost:2379 --peer fraud-checker
```

### Output

```
  - abc12345-...-6789 -> removed
  - def67890-...-1234 -> removed
Removed: 2, not found: 0
```

---

## pal intercept diff - Compare Bundle Against Directory

Compare a YAML bundle file against the current directory state to see what would change if applied.

### Synopsis

```bash
pal intercept diff [OPTIONS] FILE
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `FILE` | Path to a YAML bundle file |

### Examples

```bash
pal intercept diff -d localhost:2379 fraud-check.yaml
```

### Output

Each intercept is shown with a marker:

- `+` --- would be created (not in directory)
- `=` --- unchanged (already exists and matches)
- `~` --- modified (exists but differs)

```
Comparing bundle "fraud-check-v1" against directory...
  + BEFORE com.acme.payment.OrderService.placeOrder   (would be created)
  = AROUND com.acme.payment.OrderService.refund   (already exists, matches)
  ~ AFTER com.acme.payment.OrderService.status   (exists, but differs: callback method changed)

Summary: 1 to create, 1 unchanged, 1 to update
```

---

## pal intercept status - Check Bundle Status

Check whether the intercepts in a bundle are currently active in the directory.

### Synopsis

```bash
pal intercept status [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-f, --file FILE` | Check status of intercepts defined in a YAML bundle file |
| `--bundle NAME` | Check status using stored bundle metadata |

One of `-f` or `--bundle` must be specified.

### Examples

```bash
# Check status from a YAML file
pal intercept status -d localhost:2379 -f fraud-check.yaml

# Check status by bundle name
pal intercept status -d localhost:2379 --bundle fraud-check-v1
```

### Output

```
Bundle "fraud-check-v1" (peer: fraud-checker / 00000000-...-0002)
  + BEFORE com.acme.payment.OrderService.placeOrder   registered
  + AROUND com.acme.payment.OrderService.refund   registered
  - AFTER com.acme.payment.OrderService.status   not found

Status: 2/3 active
```

---

## pal log print - Print Messages from a Log

Print and stream messages from Kafka or Chronicle logs.

### Synopsis

```bash
pal log print [OPTIONS] LOG
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `LOG` | Log name, UUID, or `file:` path |

### Options

| Option | Description |
|--------|-------------|
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-o, --offset <number>` | Print message at specific offset. Combine with `-f` to wait for a future offset |
| `-f, --follow` | Follow new messages (like `tail -f`) |
| `--with-return` | Also print the return value or exception for the message at `--offset` |
| `--compact` | Compact output format (default) |
| `--json` | JSON output format |
| `--full` | Full output format with all details |
| `--tree` | Tree output format showing operation nesting |
| `--filter <key=value>` | Filter messages by pattern (repeatable; `class=`, `method=`, and `field=` supported) |
| `--formats <list>` | Filter by message format: `BINARY`, `JSON` (comma-separated) |
| `-t, --types <list>` | Filter by message type (comma-separated, see below) |
| `--from-peer <uuid>` | Filter by peer UUID |
| `--from-thread <name>` | Filter by thread name |
| `--id <id>` | Filter by message ID |
| `-v` | Verbose output with diagnostic information |

### Message Types for Filtering

- `CONSTRUCTOR` - Object construction
- `INSTANCE_METHOD` - Instance method invocation
- `CLASS_METHOD` - Static method invocation
- `GET_STATIC` - Static field read
- `GET_FIELD` - Instance field read
- `PUT_STATIC` - Static field write
- `PUT_FIELD` - Instance field write
- `PUT_STATIC_DONE` - Static field write confirmation
- `PUT_FIELD_DONE` - Instance field write confirmation
- `RETURN_VALUE` - Method return value
- `THROWABLE` - Exception/error

### Output Formats

**COMPACT** (default):
```
offset=42 id=abc123 message=ClassName.methodName(...)
```

**FULL**:
```
CONTEXT: offset: 42 key: peer-uuid
HEADERS: {message-type: EXEC_CLASS_METHOD, message-format: BINARY, ...}
{
  "detailed": "json representation"
}
```

**TREE**:
```
[0] com.example.App.main(String[])
  [1] com.example.Service.process()
    [2] com.example.Dao.query()
    [3] ← returned
  [4] ← returned
[5] ← returned
```

Displays messages with indentation reflecting call nesting. Operations (method calls, constructors) increase nesting depth; return values and exceptions decrease it. Useful for understanding call hierarchies at a glance.

**JSON**:
```
offset: 42,
{
  "json": "representation"
}
```

### Examples

#### Registry Mode

```bash
# Print all messages from a Kafka log in compact format
pal log print -d localhost:2379 my-wal-log

# Print messages from a Chronicle log in full format
pal log print -d localhost:2379 my-chronicle-log --full

# Print message at specific offset
pal log print -d localhost:2379 my-log -o 100

# Wait for and print message at future offset (follow mode)
pal log print -d localhost:2379 my-log -o 999 -f

# Follow new messages (like tail -f)
pal log print -d localhost:2379 my-log -f

# Print only method call messages
pal log print -d localhost:2379 my-log -t CLASS_METHOD,INSTANCE_METHOD

# Print messages in JSON format
pal log print -d localhost:2379 my-log --json

# Print messages from specific peer
pal log print -d localhost:2379 my-log --from-peer <peer-uuid>

# Verbose output with diagnostics
pal log print -d localhost:2379 my-log -v

# Print messages as an indented operation tree
pal log print -d localhost:2379 my-log --tree

# Print a specific operation and its return value
pal log print -d localhost:2379 my-log -o 42 --with-return

# Print a specific operation and its return value in full format
pal log print -d localhost:2379 my-log -o 42 --with-return --full

# Filter messages by class name (substring match)
pal log print -d localhost:2379 my-log --filter "class=OrderService"

# Filter messages by method name
pal log print -d localhost:2379 my-log --filter "method=processOrder"

# Filter messages by field name (field operations only)
pal log print -d localhost:2379 my-log --filter "field=count"

# Combine multiple filters (AND logic)
pal log print -d localhost:2379 my-log --filter "class=OrderService" --filter "method=process"

# Combine class and field filters to find field access on a specific class
pal log print -d localhost:2379 my-log --filter "class=OrderService" --filter "field=total"
```

#### Direct Mode

```bash
# Print from Chronicle log (absolute path)
pal log print file:/tmp/my-chronicle-log --full

# Print from Chronicle log (relative path)
pal log print file:./logs/my-log

# Print from Kafka log (direct, with -k option)
pal log print -k localhost:29092 my-kafka-topic

# Print from Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal log print my-kafka-topic

# Follow new messages from Chronicle log
pal log print file:/tmp/my-log -f

# Print message at specific offset from Chronicle log
pal log print file:/tmp/my-log -o 100

# Combine direct mode with filters
pal log print -k localhost:29092 my-topic -t CLASS_METHOD -f

# Tree view from Chronicle log
pal log print file:/tmp/my-log --tree

# Operation with return value from Chronicle log
pal log print file:/tmp/my-log -o 0 --with-return

# Filter by class from Kafka log
pal log print -k localhost:29092 my-topic --filter "class=OrderService"
```

---

## pal peer print - Print Messages from a Peer

Subscribe to a peer's message stream and print messages in real-time.

### Synopsis

```bash
pal peer print [OPTIONS] PEER
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `PEER` | Peer UUID or address (tcp:// or ws://) |

### Options

| Option | Description |
|--------|-------------|
| `--compact` | Compact output format (default) |
| `--json` | JSON output format |
| `--full` | Full output format with all details |
| `--tree` | Tree output format showing operation nesting |
| `-t, --types <list>` | Filter by message type (comma-separated) |
| `--from-peer <uuid>` | Filter by peer UUID |
| `--from-thread <name>` | Filter by thread name |
| `--id <id>` | Filter by message ID |
| `-v` | Verbose output |

### Examples

```bash
# Subscribe to peer by UUID
pal peer print -d localhost:2379 550e8400-e29b-41d4-a716-446655440000

# Subscribe to peer by address
pal peer print tcp://localhost:5555

# Subscribe with message type filter
pal peer print -d localhost:2379 550e8400-e29b... -t CLASS_METHOD
```

### Notes (print commands)

- **Offset behavior**:
  - For Kafka logs: offset refers to Kafka partition offset
  - For Chronicle logs: offset refers to queue index
  - When `-o` is specified without `--with-return`, all other filters are ignored
- **`--with-return`**: Must be used with `--offset`. After printing the message at the given offset, scans forward for the matching completion message and prints it. Works with method calls (`RETURN_VALUE`/`THROWABLE`), field gets (`RETURN_VALUE`), and field puts (`PUT_STATIC_DONE`/`PUT_FIELD_DONE`)
- **`--filter`**: Supports `class=<substring>`, `method=<substring>`, and `field=<substring>` patterns. The `field=` key matches only field operations (get/put static and instance fields). The `method=` key matches method names and field names. Multiple `--filter` options apply AND logic (all must match). Uses substring matching, so `class=Order` matches `com.example.OrderService`
- **`--tree`**: Shows operation nesting with indentation. Method calls and constructors increase depth; return values and exceptions decrease it. Incompatible with `--json` and `--full`
- **Follow mode** (`-f`): Waits for new messages indefinitely (use Ctrl-C to exit)
- **Log resolution**: Can specify log by name or UUID
- **Chronicle vs Kafka**: The tool automatically detects log type from directory registration
- **Performance**: Compact format is fastest, Full format includes all context

---

## pal peer call - Send RPC Calls to a Peer

Invoke methods on a remote peer via RPC.

### Synopsis

```bash
pal peer call [OPTIONS] PEER [class args...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `PEER` | Peer UUID, address (tcp:// or ws://), or name |
| `class` | Fully qualified class name |
| `args...` | Arguments passed to the method |

### Options

| Option | Description |
|--------|-------------|
| `-r, --rpc-type <ZMQ_RPC\|JSON_RPC>` | RPC type to use |
| `-m, --method <name>` | Method name to call (default: `main`) |
| `-a, --add-ids` | Auto-generate missing JSON-RPC request IDs |
| `--print-responses <bool>` | Print response messages (default: `true`) |
| `-t, --num-threads <N>` | Number of parallel clients (default: `1`) |
| `--thread-affinity <affinity>` | Thread affinity hint for the target peer |
| `-v` | Verbose output |

### Invocation Modes

#### 1. CLI Mode (Static Methods)

Invokes static methods with `String[]` signature using command-line arguments.

**Requirements**:

- Method must have signature: `static void methodName(String[] args)`
- Uses `-m` option to specify method name
- Works with both `ZMQ_RPC` and `JSON_RPC`

```bash
# Call main method (default)
pal peer call -d localhost:2379 my-peer com.example.MyClass arg1 arg2

# Call specific method
pal peer call -d localhost:2379 my-peer -m processArgs com.example.MyClass arg1 arg2

# Explicit RPC type
pal peer call -d localhost:2379 my-peer -r ZMQ_RPC com.example.MyClass
```

#### 2. JSON-RPC Stdin Mode

Sends arbitrary JSON-RPC requests via stdin for full flexibility.

**Capabilities**:

- Call any method with any signature
- Construct objects (constructors)
- Get/set fields (static and instance)
- Multiple operations in sequence

**JSON-RPC Request Format**:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-id",
  "method": "call|new|get|put",
  "params": {
    "type": "com.example.ClassName",
    "method": "methodName",
    "args": [
      {"type": "java.lang.String", "value": "arg1"}
    ]
  }
}
```

### Examples

#### Basic Method Invocation

```bash
# Call main method on peer by name
pal peer call -d localhost:2379 my-peer com.example.App arg1 arg2

# Call non-main method
pal peer call -d localhost:2379 my-peer -m processData com.example.Processor data1 data2

# Call using peer address instead of name
pal peer call tcp://localhost:5001 com.example.App

# Call using JSON-RPC endpoint
pal peer call ws://localhost:9001 com.example.App
```

#### JSON-RPC via Stdin

```bash
# Call method with custom signature
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Math","method":"add","args":[{"type":"int","value":5},{"type":"int","value":3}]}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001

# Construct object
echo '{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.User"}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001

# Get static field
echo '{"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"com.example.Config","field":"VERSION"}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001

# Set static field
echo '{"jsonrpc":"2.0","id":"1","method":"put","params":{"type":"com.example.Config","field":"debugMode","value":true}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001

# Multiple requests (one per line)
cat <<EOF | pal peer call -d localhost:2379 ws://localhost:9001
{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Math","method":"add","args":[{"type":"int","value":5},{"type":"int","value":3}]}}
{"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.example.Math","method":"multiply","args":[{"type":"int","value":5},{"type":"int","value":3}]}}
EOF

# Auto-generate missing IDs
cat requests.jsonl | pal peer call -d localhost:2379 ws://localhost:9001 -a
```

#### Performance Testing

```bash
# Single-threaded call
pal peer call -d localhost:2379 my-peer com.example.Benchmark

# Multi-threaded calls (10 parallel clients)
pal peer call -d localhost:2379 my-peer -t 10 com.example.Benchmark
```

### RPC Type Selection

The tool automatically infers RPC type based on:

1. Explicit `-r/--rpc-type` option
2. Peer address scheme:
   - `tcp://` → `ZMQ_RPC`
   - `ws://` → `JSON_RPC`
3. Peer's registered endpoints in directory

If a peer supports both RPC types, you must specify `-r` explicitly.

---

## pal log call - Send Method Calls via a Log

Write method call messages to a log (Kafka or Chronicle).

### Synopsis

```bash
pal log call [OPTIONS] [LOG] [class args...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `LOG` | Log name, topic, or `file:/path` |
| `class` | Fully qualified class name |
| `args...` | Arguments passed to the method |

### Options

| Option | Description |
|--------|-------------|
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-i, --input-log <name>` | Read responses from this log |
| `-o, --output-log <name>` | Write requests to this log |
| `-f, --forget-response` | Send without waiting for response (async) |
| `-m, --method <name>` | Method name to call (default: `main`) |
| `--print-responses <bool>` | Print response messages (default: `true`) |
| `-t, --num-threads <N>` | Number of parallel clients (default: `1`) |
| `-v` | Verbose output |

### Examples

#### Registry Mode

```bash
# Write method call to log (async, no response)
pal log call -d localhost:2379 my-log -f com.example.Worker process

# Write to output log, read response from input log
pal log call -d localhost:2379 -i input-log -o output-log com.example.App
```

#### Direct Mode

```bash
# Write to Chronicle log (no PAL directory needed)
pal log call file:/tmp/my-log -f com.example.Worker process

# Write to Chronicle log (relative path)
pal log call file:./logs/my-log com.example.App

# Write to Kafka log (with -k option)
pal log call -k localhost:29092 my-topic -f com.example.App

# Write to Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal log call my-kafka-topic com.example.Processor data1 data2
```

### Notes (call commands)

- **CLI mode limitations**: Only works with `static void methodName(String[] args)` signature
- **JSON-RPC flexibility**: Use stdin mode with `pal peer call` for arbitrary method signatures, constructors, and field access
- **Peer resolution**: Can specify peer by UUID, name, or direct RPC address
- **Async writes**: `--forget-response` only works with log calls
- **Response printing**: Use `--print-responses false` to suppress output for performance testing
- **Multi-threading**: Each thread creates its own client instance

---

## pal peer rm - Remove Peers

Remove peers from the directory.

### Synopsis

```bash
pal peer rm [OPTIONS] [PEER...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `PEER` | Peer names or UUIDs to remove |

### Options

| Option | Description |
|--------|-------------|
| `-s, --starting-with` | Treat arguments as prefixes (delete all matching) |
| `-a, --all` | Delete all peers |
| `-f, --force` | Skip confirmation prompts and force removal of alive peers |

### Examples

```bash
# Remove peer by name
pal peer rm -d localhost:2379 my-peer

# Remove peer by UUID
pal peer rm -d localhost:2379 550e8400-e29b-41d4-a716-446655440000

# Remove multiple peers
pal peer rm -d localhost:2379 peer-alpha peer-beta peer-gamma

# Remove all peers with prefix
pal peer rm -d localhost:2379 -s test-peer

# Force remove live peer
pal peer rm -d localhost:2379 my-running-peer --force
```

---

## pal log rm - Remove Logs

Remove logs from the directory and their backing storage.

### Synopsis

```bash
pal log rm [OPTIONS] [LOG...]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `LOG` | Log names, UUIDs, or `file:` paths to remove |

### Options

| Option | Description |
|--------|-------------|
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-s, --starting-with` | Treat arguments as prefixes (delete all matching) |
| `-a, --all` | Delete all logs |
| `-f, --force` | Skip confirmation prompts |

### Examples

#### Registry Mode

```bash
# Remove single log by name
pal log rm -d localhost:2379 my-old-log

# Remove log by UUID
pal log rm -d localhost:2379 550e8400-e29b-41d4-a716-446655440000

# Remove multiple logs
pal log rm -d localhost:2379 log1 log2 log3

# Remove all logs with prefix
pal log rm -d localhost:2379 -s test-log

# Remove all logs (dangerous!)
pal log rm -d localhost:2379 -a --force
```

#### Direct Mode

```bash
# Remove Chronicle log (no PAL directory needed)
pal log rm file:/tmp/my-chronicle-log

# Remove Chronicle log (relative path)
pal log rm file:./logs/old-log

# Remove Kafka log (with -k option)
pal log rm -k localhost:29092 my-kafka-topic

# Remove Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal log rm my-old-topic

# Note: Direct mode removes from backing store only
# If log was registered in PAL directory, use registry mode to fully clean up
```

### Safety Features

- **Live peer protection**: Cannot remove peers with active leases without `--force`
- **Confirmation prompts**: Asks before deleting multiple items (unless `--force`)
- **Error reporting**: Returns count of errors encountered

### Notes (rm commands)

- **Chronicle logs**: Deletion removes queue directory and all files
- **Kafka logs**: Deletion removes topic from Kafka cluster
- **Live peers**: Must use `--force` to remove peers with active leases
- **Name vs UUID**: Arguments can be names or UUIDs; the tool auto-detects
- **Prefix matching** (`-s`): Useful for bulk cleanup of test resources
- **Return code**: Returns the number of errors encountered (0 = success)
- **Direct mode**: Removes from backing store only (doesn't unregister from PAL directory if it was registered there)
- **Registry mode vs Direct mode**: Use registry mode for full cleanup (unregister + delete backing store), use direct mode for quick local log deletion

---

## pal replay - Deterministic WAL Replay

Re-execute an application from `main()` while verifying every operation against a previously recorded WAL. The WAL acts as an oracle: each operation that the application performs is matched against the corresponding WAL entry, and the return value is compared. Any difference is reported as a divergence.

This is not "playing back a recording." The application runs naturally from `main()`, hitting the same AspectJ call sites as during the original execution. The replay system verifies that every operation produces the same result.

### Synopsis

```bash
pal replay [OPTIONS] class [args...]
```

### Options

| Option | Description |
|--------|-------------|
| `-w, --wal <name\|file:/path>` | **(Required)** WAL to replay from. Use `file:/path` for Chronicle Queue or a topic name for Kafka. Relative Chronicle paths (e.g., `file:app.wal`) are resolved against the current working directory |
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (required for Kafka WAL topics without `-d`) |
| `--divergence-policy <WARN\|HALT\|IGNORE>` | Action on divergence (default: `WARN`) |
| `--threading <ordered\|unordered>` | Thread ordering for multi-threaded replay (default: `ordered`). See [Multi-Threaded Replay](#multi-threaded-replay) |
| `--delay <milliseconds>` | Delay before each entry-point injection for slow-motion replay visualization (default: `0`, disabled). See [Slow-Motion Replay](#slow-motion-replay) |
| `--policy <path>` | Path to a YAML replay policy file. See [Side-Effect Shielding](#side-effect-shielding) |
| `--shield-io` | Enable built-in I/O stubbing rules (time, random, I/O streams, JDBC). See [Side-Effect Shielding](#side-effect-shielding) |
| `--re-execute <patterns>` | Comma-separated Ant-style patterns for classes/methods to re-execute (highest priority) |
| `--stub <patterns>` | Comma-separated Ant-style patterns for classes/methods to stub from WAL |
| `--stub-all-else` | Stub all operations not matched by explicit `--re-execute` rules (sets default action to `STUB_FROM_WAL`) |
| `--force-stub` | Proceed with replay even if unsafe stubs are detected by the side-effect analyzer |
| `-cp, --classpath <CLASSPATH>` | Classpath for the application (required when replaying a class) |
| `-jar <jarFile>` | JAR file to replay (Main-Class from manifest). Alternative to specifying a main class |
| `--fx-thread` | Enable JavaFX Application Thread execution. Required for replaying JavaFX applications (default: `false`) |
| `--scope <patterns>` | Ant-style class patterns for recording scope (must match the flags used during recording). See [Recording Scope](concepts/recording-scope.md) |
| `--scope-exclude <patterns>` | Ant-style class patterns to exclude from recording scope (must match recording) |
| `--scope-io` | Include built-in I/O boundary rules in recording scope (must match recording) |
| `--scope-policy <path>` | Path to YAML recording scope policy file (must match recording) |
| `--scope-default <record\|skip>` | Default recording scope action (must match recording) |

**Recording scope on replay**: When the WAL was recorded with `--scope` flags, the same flags **must** be passed to `pal replay`. The replay system uses the scope to determine which operations have WAL entries (in-scope) and which should be executed directly without WAL matching (out-of-scope). Mismatched scope flags produce cascading divergences. See [Recording Scope — Replay Requirement](concepts/recording-scope.md#replay-requirement).

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `class` | Fully qualified main class to replay (required unless using `-jar`) |
| `args...` | Application arguments passed to `main()` |

### Divergence Policies

| Policy | Behavior |
|--------|----------|
| `WARN` | Log each divergence to stderr and continue (default) |
| `HALT` | Stop immediately on the first divergence |
| `IGNORE` | Silently record divergences without logging |

### Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Replay completed with zero divergences |
| `1` | Application error (missing class, uncaught exception) |
| `2` | Divergences detected between live execution and WAL |

### How It Works

1. The WAL is loaded and indexed. Operations and their return values are paired (each method call is matched to its return value entry).
2. The application starts from `main()` with the provided arguments.
3. At every quantized operation (method call, constructor, field access), the replay system:
   - Matches the live operation's signature against the next expected WAL entry
   - Executes the operation normally
   - Compares the actual return value against the WAL-recorded value
   - Reports a divergence if they differ
4. After the application completes, a divergence report is printed to stderr (if any).

### Examples

#### Replay from Chronicle Queue

```bash
# Step 1: Record a WAL
pal run --wal file:/tmp/my-wal -cp target/classes com.example.App arg1 arg2

# Step 2: Replay from the recorded WAL
pal replay --wal file:/tmp/my-wal -cp target/classes com.example.App arg1 arg2
```

#### Replay with a relative WAL path

```bash
# Record a WAL in the current directory
pal run --wal file:./app.wal -cp target/classes com.example.App arg1 arg2

# Replay using a relative path — resolved against the current working directory
pal replay --wal file:app.wal -cp target/classes com.example.App arg1 arg2
```

#### Replay from Kafka

```bash
# Step 1: Record to Kafka
pal run -d localhost:2379 -k localhost:29092 --wal my-topic \
  -cp app.jar com.example.App

# Step 2: Replay from Kafka (with explicit servers)
pal replay --wal my-topic -k localhost:29092 \
  -cp app.jar com.example.App

# Or with PAL directory (resolves Kafka servers automatically)
pal replay -d localhost:2379 --wal my-topic \
  -cp app.jar com.example.App
```

#### Detecting Divergences

```bash
# Record with one set of arguments
pal run --wal file:/tmp/baseline -cp target/classes com.example.App input-A

# Replay with different arguments — produces divergences
pal replay --wal file:/tmp/baseline -cp target/classes com.example.App input-B
# Exit code: 2
# stderr shows: [VALUE_MISMATCH] offset=N: expected "X" but got "Y"
```

#### Halt on First Divergence

```bash
pal replay --wal file:/tmp/my-wal --divergence-policy HALT \
  -cp target/classes com.example.App
```

### Slow-Motion Replay

For UI applications (JavaFX, Swing), operations can happen too fast to observe during replay. Use `--delay` to add a pause before each entry-point injection:

```bash
# 2-second delay between entry points (good for visual debugging)
pal replay --wal file:/tmp/fx-wal --fx-thread --delay 2000 \
  -jar target/my-javafx-app.jar
```

The delay is specified in milliseconds. Use larger values (2000-5000ms) to observe each UI state change, smaller values (200-500ms) for faster but still visible replay.

### Multi-Threaded Replay

When the WAL contains operations from multiple threads (e.g., RPC worker threads), replay automatically detects entry-point operations and spawns `ReplayInputInjector` threads to re-inject them. No additional configuration is required beyond ensuring `--wal-incoming-rpc` was enabled during recording (this is the default).

The `--threading` option controls cross-thread ordering:

| Value | Behavior |
|-------|----------|
| `ordered` (default) | Entry-point injection follows WAL-offset ordering. Preserves the recorded execution order across threads. |
| `unordered` | Entry-point injection runs without ordering constraints. Faster, but cross-thread order may differ from the recording. |

```bash
# Replay a multi-threaded RPC service (ordered by default)
pal replay --wal file:/tmp/service-wal -cp target/classes com.example.ServiceMain

# Replay without cross-thread ordering constraints
pal replay --wal file:/tmp/service-wal --threading unordered \
  -cp target/classes com.example.ServiceMain
```

See the [Deterministic Replay Guide](guides/deterministic-replay.md#multi-threaded-replay) for a complete walkthrough.

### Side-Effect Shielding

By default, all operations are re-executed during replay. Side-effect shielding allows operations to be **stubbed** — returning WAL-recorded values without executing — for I/O, databases, time, randomness, and other non-deterministic or unavailable resources.

See the [Deterministic Replay Guide](guides/deterministic-replay.md#side-effect-shielding-replay-policy) for detailed configuration guidance.

#### Built-in I/O Shielding

```bash
# Stub common non-deterministic operations (time, random, I/O, JDBC)
pal replay --wal file:/tmp/my-wal --shield-io \
  -cp target/classes com.example.App
```

#### YAML Policy File

```bash
# Apply a custom replay policy
pal replay --wal file:/tmp/my-wal --policy policy.yaml \
  -cp target/classes com.example.App
```

#### CLI Pattern Flags

```bash
# Stub specific operations
pal replay --wal file:/tmp/my-wal \
  --stub "java.lang.System.currentTimeMillis,java.io.**.**" \
  -cp target/classes com.example.App

# Re-execute only your code, stub everything else
pal replay --wal file:/tmp/my-wal \
  --re-execute "com.example.**" --stub-all-else \
  -cp target/classes com.example.App

# Combine approaches
pal replay --wal file:/tmp/my-wal --shield-io \
  --re-execute "com.example.TimeService.**" \
  -cp target/classes com.example.App
```

#### Unsafe Stub Override

```bash
# Proceed despite unsafe stub warnings
pal replay --wal file:/tmp/my-wal --policy policy.yaml --force-stub \
  -cp target/classes com.example.App
```

### Notes

- Replay is **read-only**: no new WAL is written during replay. The recorded WAL is consumed but not modified.
- The application must be compiled with the same AspectJ weaving as during recording. Class version mismatches will surface as operation mismatches.
- **Multi-threaded replay** is supported for applications that receive input on multiple threads (RPC services, web apps, Swing applications). Entry-point operations must be captured in the WAL during recording (`--wal-incoming-rpc`, enabled by default).
- **JavaFX applications** require `--fx-thread` during both recording and replay. This routes UI event handlers to the real JavaFX Application Thread.
- When recording a WAL intended for replay, use `--no-wal-incoming-cli` if you want the WAL to contain only the hot-path operations (excluding the bootstrap `main()` wrapper). This is often the right choice for cleaner replay matching.

---

## pal log index - Analyze WAL Structure

Index a WAL and print a structural summary: entry counts, operation/completion pairing, threads, and any structural issues (orphaned or unmatched entries).

### Synopsis

```bash
pal log index [OPTIONS] file:/path             # Chronicle Queue
pal log index -k <servers> [OPTIONS] <topic>   # Kafka
pal log index -d <url> [OPTIONS] <name>        # PalDirectory
```

### Options

| Option | Description |
|--------|-------------|
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (required for Kafka topics without `-d`) |
| `-v, --verbose` | Show per-entry detail listing before the summary |

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `name\|file:/path` | **(Required)** Log path: `file:/path` for Chronicle Queue, or topic name for Kafka |

### Output

**Summary** (always printed):

```
WAL Index Summary
  Entries:     142
  Operations:  71
  Completions: 71
  Pairs:       71
  Threads:     [main]
  Issues:      0
```

- **Entries**: Total WAL entries
- **Operations**: Entries that open a scope (method calls, constructors, field access)
- **Completions**: Entries that close a scope (return values, exceptions, field-write confirmations)
- **Pairs**: Number of matched operation/completion pairs
- **Threads**: Thread names found in the WAL
- **Issues**: Structural problems (orphaned completions without a matching operation, or operations without a completion)

**Verbose** (`-v`, printed before summary):

```
[0] OPERATION main MinimalReceiptCalculator.main(String[])
[1] OPERATION main MinimalReceiptCalculator.parseCart(String[])
[2] OPERATION main HashMap.new()
[3] COMPLETION main HashMap
...
```

Each entry shows: `[offset] kind threadName className.executableName(paramTypes)`

For multi-threaded WALs recorded with `--wal-incoming-rpc`, entry-point operations (incoming RPC calls that initiate a new causal chain on a non-self-caller thread) are marked in the WAL. These entry-point markers are used by the replay system's `ReplayInputInjector` to identify which operations to re-inject during multi-threaded replay.

### Examples

```bash
# Analyze a Chronicle WAL
pal log index file:/tmp/my-wal

# Analyze with per-entry detail
pal log index --verbose file:/tmp/my-wal

# Analyze a Kafka WAL
pal log index -k localhost:29092 my-topic

# Analyze via PAL directory
pal log index -d localhost:2379 my-log-name
```

### Notes

- A balanced WAL has equal Operations and Completions counts and zero Issues. Imbalances indicate the application was interrupted mid-execution or the WAL was truncated.
- This command is useful for verifying a WAL before replay, and for understanding the structure of recorded executions.

---

## pal log stats - Show Log Message Statistics

Display message statistics for a log.

### Synopsis

```bash
pal log stats [OPTIONS] [LOG_NAME]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `LOG_NAME` | Log name |

### Options

| Option | Description |
|--------|-------------|
| `-b, --bootstrap-servers <host:port>` | Kafka bootstrap servers (default: `localhost:9092`) |
| `-t, --types <list>` | Filter by message type(s) |
| `-fp, --from-peer <uuid>` | Filter by peer UUID |
| `-ft, --from-thread <name>` | Filter by thread name |
| `-j, --json-output` | Print stats as JSON |
| `-v` | Verbose output |

### Examples

```bash
# Show stats for a log
pal log stats -b localhost:29092 my-wal

# Show stats as JSON
pal log stats -b localhost:29092 my-wal -j

# Filter by message type
pal log stats -b localhost:29092 my-wal -t INSTANCE_METHOD,CLASS_METHOD
```

---

## pal peer stats - Show Peer Message Statistics

Display message statistics for a peer's message stream.

### Synopsis

```bash
pal peer stats [OPTIONS] [PEER]
```

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `PEER` | Peer UUID or address |

### Options

| Option | Description |
|--------|-------------|
| `-t, --types <list>` | Filter by message type(s) |
| `-fp, --from-peer <uuid>` | Filter by peer UUID |
| `-ft, --from-thread <name>` | Filter by thread name |
| `-j, --json-output` | Print stats as JSON |

### Examples

```bash
# Show stats for a peer
pal peer stats -d localhost:2379 550e8400-e29b-41d4-a716-446655440000

# Show stats as JSON
pal peer stats -d localhost:2379 my-peer -j
```

---

## Common Patterns

### Development Workflow

```bash
# Start development peer
pal run -d localhost:2379 -k localhost:29092 -n dev-peer --zmq-rpc auto -cp target/classes

# List running peers
pal peer ls -d localhost:2379

# Call method on dev peer
pal peer call -d localhost:2379 dev-peer com.example.TestApp

# View peer's WAL
pal log print -d localhost:2379 dev-peer-wal -f

# Cleanup when done
pal peer rm -d localhost:2379 dev-peer --force
pal log rm -d localhost:2379 dev-peer-wal
```

### Testing and Debugging

```bash
# Run test class and capture to log
pal run -d localhost:2379 -k localhost:29092 --wal test-run -cp target/test-classes com.example.MyTest

# Replay and analyze
pal log print -d localhost:2379 test-run --full -t CLASS_METHOD

# View call tree
pal log print -d localhost:2379 test-run --tree

# Print specific message and its return value
pal log print -d localhost:2379 test-run -o 42 --with-return

# Filter to specific class
pal log print -d localhost:2379 test-run --filter "class=OrderService"

# Cleanup test artifacts
pal log rm -d localhost:2379 -s test- --force
```

### Distributed System Monitoring

```bash
# List all active peers in cluster
pal peer ls -d etcd.prod.example.com:2379 -l

# Monitor specific peer's output
pal peer print -d etcd.prod.example.com:2379 <peer-uuid>

# View shared log for debugging
pal log print -d etcd.prod.example.com:2379 shared-events -f -t THROWABLE
```

### Performance Analysis

```bash
# Benchmark with multiple threads
pal peer call -d localhost:2379 bench-peer -t 10 com.example.Benchmark -v

# Analyze log for performance metrics
pal log print -d localhost:2379 bench-wal --compact | grep -E "method=process"
```

---

## Environment Variables

### JVM Configuration (`pal run`)

These variables control how the JVM is launched for peer processes. See the [JVM Configuration](guides/jvm-configuration.md) guide for full details and examples.

| Variable | Default | Description |
|----------|---------|-------------|
| `PAL_HEAP_OPTS` | `-Xmx1g` | Heap sizing (replaces default when set) |
| `PAL_GC_OPTS` | G1GC, 200ms pause | GC selection and tuning (replaces default when set) |
| `PAL_JMX_OPTS` | _(from HOST/PORT)_ | Full JMX configuration (replaces default when set) |
| `PAL_JMX_HOST` | _(unset)_ | JMX hostname (convenience, used to build `PAL_JMX_OPTS`) |
| `PAL_JMX_PORT` | _(unset)_ | JMX port (convenience, used to build `PAL_JMX_OPTS`) |
| `PAL_JAVA_OPTS` | _(empty)_ | Catch-all JVM flags, appended last (always wins) |
| `JAVA_AGENT` | _(unset)_ | Path to a Java agent JAR |

### Logging

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_PEER_LOGGING_CONFIG` | — | _(unset)_ | PAL peer Logback configuration file (not application logging) |
| `PAL_CLI_LOGGING_CONFIG` | — | _(unset)_ | PAL CLI Logback configuration file (not application logging) |

### Peer

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_DIRECTORY` | `-d, --dir` | _(unset)_ | PAL directory URL (etcd endpoint, `HOST:PORT`). If unset, the peer runs unregistered |
| `PAL_PEER_UUID` | `-u, --uuid` | _(random)_ | Unique peer identifier. Auto-generated if not set |
| `PAL_PEER_NAME` | `-n, --name` | _(unset)_ | Human-readable peer name for directory registration |

### WAL/PUB

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_WAL` | `-w, --wal` | _(unset)_ | Write-ahead log destination. `auto`, `file:/path` (Chronicle), or Kafka topic name |
| `PAL_LOG` | `-l, --log` | _(unset)_ | Shorthand to use the same topic/queue for both source-log and WAL |
| `PAL_SOURCE_LOG` | `-s, --source-log` | _(unset)_ | Source log to consume messages from. `auto`, `file:/path`, or Kafka topic |
| `PAL_LOG_PREFIX` | `--log-prefix` | `app` | Prefix for auto-generated log names |
| `PAL_WAL_INCOMING_RPC` | `--wal-incoming-rpc` | `true` | Write incoming RPC calls (ZMQ, JSON-RPC, CLI) to WAL/PUB |
| `PAL_WAL_ALL_INCOMING_RPC` | `--wal-all-incoming-rpc` | `false` | Write ALL incoming RPC calls to WAL/PUB including LOG_RPC channel. Implies `PAL_WAL_INCOMING_RPC` |
| `PAL_WAL_INCOMING_CLI` | `--wal-incoming-cli` | `true` | Write incoming CLI bootstrap calls to WAL/PUB |
| `PAL_WITH_SOURCE_CONTEXT` | `--with-source-context` | `false` | Include source context in log messages |

### RPC

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_ZMQ_RPC` | `-r, --zmq-rpc` | _(unset)_ | ZMQ-RPC listener. `[HOST:]PORT` or `auto` |
| `PAL_JSON_RPC` | `-j, --json-rpc` | _(unset)_ | JSON-RPC WebSocket listener. `[HOST:]PORT` or `auto` |
| `PAL_TCP_PUB` | `-p, --tcp-pub` | _(unset)_ | TCP publication endpoint (ZeroMQ). `[HOST:]PORT` or `auto` |
| `PAL_RPC_THREADS` | `--rpc-threads` | `1` | Number of RPC handler threads |
| `PAL_RPC_POLICY` | `--rpc-policy` | _(unset)_ | Path to RPC access policy YAML file |
| `PAL_RPC_POLICY_PRESET` | `--rpc-policy-preset` | _(unset)_ | Comma-separated list of policy presets (e.g., `deny-unsafe,deny-jdk-internals`) |
| `PAL_RPC_DEFAULT_ACTION` | `--rpc-default-action` | `DENY` | Default action when no policy rule matches (`ALLOW` or `DENY`) |
| `PAL_RPC_POLICY_WATCH_INTERVAL` | `--rpc-policy-watch-interval` | `2000` | Policy file watch interval in ms. `0` to disable |
| `PAL_FX_THREAD` | `--fx-thread` | `false` | Route RPC calls with `fx-thread` affinity to the JavaFX Application Thread |

### Interception

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_INTERCEPTABLE` | `--interceptable` | `false` | Enable message interception (requires PAL directory) |
| `PAL_IN_FLIGHT_TRACKING` | `--in-flight-tracking` | `true` | Track in-flight dispatches for safe intercept activation |
| `PAL_DRAIN_TIMEOUT_MS` | `--drain-timeout-ms` | `5000` | Timeout in ms for draining in-flight dispatches before intercept activation |
| `PAL_CALLBACK_TIMEOUT_MS` | `--callback-timeout-ms` | `3000` | Timeout in ms for intercept callback responses. `0` = infinite. Overridable per-intercept |
| `PAL_EXCEPTION_POLICY` | `--exception-policy` | `PROPAGATE_CONTROLLED_ONLY` | Exception propagation policy for intercept callbacks. Values: `PROPAGATE_ALL`, `PROPAGATE_EXPLICIT_ONLY`, `SWALLOW_ALL`, `PROPAGATE_CONTROLLED_ONLY` |
| `PAL_CHECKED_EXCEPTION_POLICY` | `--checked-exception-policy` | `WRAP` | Checked exception handling for intercept callbacks. Values: `WRAP`, `REJECT`, `ALLOW_ALL` |

### Infrastructure

| Variable | Flag | Default | Description |
|----------|------|---------|-------------|
| `PAL_KAFKA_SERVERS` | `-k, --kafka-servers` | _(unset)_ | Kafka bootstrap servers. Required when using Kafka-backed logs |
| `PAL_KAFKA_TIMEOUT_MS` | `--kafka-timeout` | `5000` | Kafka connection health check timeout in ms |
| `PAL_ETCD_TIMEOUT_MS` | `--etcd-timeout` | `5000` | etcd connection health check timeout in ms |
| `PAL_CHRONICLE_BASE_DIR` | `--chronicle-base-dir` | _(cwd)_ | Base directory for Chronicle queues with relative `file:` paths |
| `PAL_PROPERTIES` | `--properties` | _(unset)_ | Path to external properties file overlaying built-in defaults |

---

## Exit Codes

- `0` - Success
- `1` - Invalid arguments or command failure
- `>1` - Number of errors encountered (for `pal peer rm` / `pal log rm`)

---

## See Also

- Getting Started guide for `pal run` documentation
- PAL Architecture documentation for concepts (peers, logs, messages)
- Integration tests in `modules/itt/src/test/java/io/quasient/pal/cli/` for more examples
