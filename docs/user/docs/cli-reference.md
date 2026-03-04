# CLI Reference

PAL provides a command-line interface for managing peers, logs, and remote procedure calls. For a hands-on introduction to `pal run`, see the [Getting Started](getting-started.md) guide. This reference covers all CLI subcommands, starting with `pal run` WAL options.

## Overview

The PAL CLI uses a subcommand structure:

```bash
pal <subcommand> [OPTIONS] [ARGUMENTS]
```

Common options across all subcommands:

- `-d, --dir <URL>` - PAL directory URL (etcd endpoint, e.g., `localhost:2379`)
- `-k, --kafka-servers <host:port>` - Kafka bootstrap servers (available on `print`, `call`, and `rm` only)
- `-h, --help` - Display help for the subcommand

The directory URL can also be set via the `PAL_DIRECTORY` environment variable. Kafka servers can be set via the `KAFKA_SERVERS` environment variable.

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
pal print -d localhost:2379 -l my-log
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
pal print -l file:/tmp/my-log

# Kafka log (specify servers)
pal print -k localhost:29092 -l my-log
```

### Command Support

| Command | Registry Mode | Direct Mode | Notes |
|---------|--------------|-------------|-------|
| `pal ls` | ✓ | ✗ | Requires directory (purpose is to list directory) |
| `pal print` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal call` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal rm` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal replay` | ✓ | ✓ | Both Chronicle and Kafka |
| `pal wal-index` | ✓ | ✓ | Both Chronicle and Kafka |

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

## pal ls - List Peers, Logs, and Intercepts

List registered peers, logs, and intercepts in the directory.

### Synopsis

```bash
pal ls [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-P, --peers` | List only peers |
| `-L, --logs` | List only logs |
| `-I, --intercepts` | List only intercepts |
| `-l, --long` | Use long listing format with detailed information |
| `-S, --sort-by-size` | Sort logs by size, largest first |
| `-c, --sort-by-ctime` | Sort by creation/uptime, newest first |
| `-r, --reverse` | Reverse the sorting order |
| `--no-trim` | Disable trimming of long field values |

### Behavior

- **No flags**: Lists both peers and logs (intercepts require explicit `-I`)
- **Filter flags** (`-P`, `-L`, `-I`): Are mutually exclusive; use only one at a time
- **Short format**: Shows names (or UUIDs if no name is set)
- **Long format** (`-l`): Shows detailed information including UUIDs, endpoints, sizes, offsets, and timestamps

### Examples

```bash
# List all peers and logs
pal ls -d localhost:2379

# List only running peers (short format shows name or UUID)
pal ls -d localhost:2379 -P

# List peers with detailed information
pal ls -d localhost:2379 -P -l

# List logs sorted by creation time (newest first)
pal ls -d localhost:2379 -L -c

# List logs sorted by size (largest first) in long format
pal ls -d localhost:2379 -L -S -l

# List logs sorted by size (smallest first)
pal ls -d localhost:2379 -L -S -r

# List all registered intercepts
pal ls -d localhost:2379 -I

# List intercepts with detailed information
pal ls -d localhost:2379 -I -l

# List intercepts sorted by creation time (newest first)
pal ls -d localhost:2379 -I -c -l
```

### Long Format Output

**Peers** (`-P -l`):
```
UUID                                 Name            ZMQ-RPC              JSON-RPC             PUB                  JMX                  Uptime
```

- UUID: Peer unique identifier
- Name: Peer name (if set)
- ZMQ-RPC: Binary RPC endpoint (tcp://)
- JSON-RPC: JSON-RPC endpoint (ws://)
- PUB: Message publication endpoint (tcp://)
- JMX: JMX monitoring endpoint
- Uptime: Time since peer started (H:mm:ss format)

**Logs** (`-L -l`):
```
Name                 UUID                                 Size       Start    --> End      Created
```

- Name: Log name
- UUID: Log unique identifier
- Size: Total size in human-readable format (KB, MB, GB)
- Start: First available offset/index
- End: Last available offset/index
- Created: Creation timestamp (MMM dd HH:mm format)

**Intercepts** (`-I -l`):
```
UUID                                 Peer                                 Type         Class                          Target                    Callback                       Created
```

- UUID: Intercept request unique identifier
- Peer: UUID of the peer that registered the intercept
- Type: Intercept type (BEFORE, AFTER, AROUND, BEFORE_ASYNC, AFTER_ASYNC)
- Class: Simple name of the intercepted class
- Target: Intercepted method signature or field operation (e.g., `add(int, int)` or `counter [GET]`)
- Callback: Simple callback class name and method (e.g., `Handler.onAdd`)
- Created: Creation timestamp (MMM dd HH:mm format)

### Notes

- Lists both Kafka and Chronicle logs
- Chronicle logs use `file:` prefix in the directory but are displayed without it
- Long format truncates long values with ".." to fit columns
- Logs must exist in their backing store (Kafka or Chronicle) to be displayed
- Intercepts are listed from the etcd directory; they exist as long as the owning peer's lease is active

---

## pal print - Print Messages from Logs

Print and stream messages from Kafka/Chronicle logs or subscribe to peer message streams.

### Synopsis

```bash
# Print from log
pal print -l <LOG_NAME> [OPTIONS]

# Subscribe to peer
pal print -pu <PEER_UUID> [OPTIONS]
pal print -pa <HOST:PORT> [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-l, --log <name\|uuid\|path>` | Read messages from the specified log (name/UUID for registry mode, `file:path` for Chronicle direct mode, topic name for Kafka direct mode) |
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-pu, --peer-uuid <uuid>` | Subscribe to peer by UUID |
| `-pa, --peer-address <HOST:PORT>` | Subscribe to peer by address |
| `-o, --offset <number>` | Print message at specific offset and exit |
| `-f, --follow` | Follow new messages (like `tail -f`) |
| `--compact` | Compact output format (default) |
| `--json` | JSON output format |
| `--full` | Full output format with all details |
| `--tree` | Tree output format showing operation nesting |
| `--with-return` | Also print the return value or exception for the message at `--offset` |
| `--filter <key=value>` | Filter messages by pattern (repeatable; `class=` and `method=` supported) |
| `--formats <list>` | Filter by message format: `BINARY`, `JSON` (comma-separated) |
| `--types <list>` | Filter by message type (comma-separated, see below) |
| `-fp, --from-peer <uuid>` | Filter by peer UUID |
| `-ft, --from-thread <name>` | Filter by thread name |
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

#### Reading from Logs (Registry Mode)

```bash
# Print all messages from a Kafka log in compact format
pal print -d localhost:2379 -l my-wal-log

# Print messages from a Chronicle log in full format
pal print -d localhost:2379 -l my-chronicle-log --full

# Print message at specific offset
pal print -d localhost:2379 -l my-log -o 100

# Wait for and print message at future offset (follow mode)
pal print -d localhost:2379 -l my-log -o 999 -f

# Follow new messages (like tail -f)
pal print -d localhost:2379 -l my-log -f

# Print only method call messages
pal print -d localhost:2379 -l my-log --types CLASS_METHOD,INSTANCE_METHOD

# Print messages in JSON format
pal print -d localhost:2379 -l my-log --json

# Print messages from specific peer
pal print -d localhost:2379 -l my-log -fp <peer-uuid>

# Verbose output with diagnostics
pal print -d localhost:2379 -l my-log -v

# Print messages as an indented operation tree
pal print -d localhost:2379 -l my-log --tree

# Print a specific operation and its return value
pal print -d localhost:2379 -l my-log -o 42 --with-return

# Print a specific operation and its return value in full format
pal print -d localhost:2379 -l my-log -o 42 --with-return --full

# Filter messages by class name (substring match)
pal print -d localhost:2379 -l my-log --filter "class=OrderService"

# Filter messages by method name
pal print -d localhost:2379 -l my-log --filter "method=processOrder"

# Combine multiple filters (AND logic)
pal print -d localhost:2379 -l my-log --filter "class=OrderService" --filter "method=process"
```

#### Reading from Logs (Direct Mode)

```bash
# Print from Chronicle log (absolute path)
pal print -l file:/tmp/my-chronicle-log --full

# Print from Chronicle log (relative path)
pal print -l file:./logs/my-log

# Print from Kafka log (direct, with -k option)
pal print -k localhost:29092 -l my-kafka-topic

# Print from Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal print -l my-kafka-topic

# Follow new messages from Chronicle log
pal print -l file:/tmp/my-log -f

# Print message at specific offset from Chronicle log
pal print -l file:/tmp/my-log -o 100

# Combine direct mode with filters
pal print -k localhost:29092 -l my-topic --types CLASS_METHOD -f

# Tree view from Chronicle log
pal print -l file:/tmp/my-log --tree

# Operation with return value from Chronicle log
pal print -l file:/tmp/my-log -o 0 --with-return

# Filter by class from Kafka log
pal print -k localhost:29092 -l my-topic --filter "class=OrderService"
```

#### Subscribing to Peers

```bash
# Subscribe to peer by UUID
pal print -d localhost:2379 -pu <peer-uuid>

# Subscribe to peer by address
pal print -pa tcp://localhost:5555

# Subscribe with message type filter
pal print -d localhost:2379 -pu <peer-uuid> --types CLASS_METHOD
```

### Notes

- **Offset behavior**:
  - For Kafka logs: offset refers to Kafka partition offset
  - For Chronicle logs: offset refers to queue index
  - When `-o` is specified without `--with-return`, all other filters are ignored
- **`--with-return`**: Must be used with `--offset`. After printing the message at the given offset, scans forward for a matching `RETURN_VALUE` or `THROWABLE` message (matched by message ID) and prints it too
- **`--filter`**: Supports `class=<substring>` and `method=<substring>` patterns. Multiple `--filter` options apply AND logic (all must match). Uses substring matching, so `class=Order` matches `com.example.OrderService`
- **`--tree`**: Shows operation nesting with indentation. Method calls and constructors increase depth; return values and exceptions decrease it. Incompatible with `--json` and `--full`
- **Follow mode** (`-f`): Waits for new messages indefinitely (use Ctrl-C to exit)
- **Log resolution**: Can specify log by name or UUID
- **Chronicle vs Kafka**: The tool automatically detects log type from directory registration
- **Performance**: Compact format is fastest, Full format includes all context

---

## pal call - Send Messages to Peers or Logs

Invoke methods on remote peers or write messages to logs using RPC.

### Synopsis

```bash
# CLI mode: Call static method with String[] parameter
pal call [OPTIONS] -p <PEER> [-m <METHOD>] <CLASS> [args...]

# JSON-RPC stdin mode: Send arbitrary JSON-RPC requests
echo '<JSON_RPC_REQUEST>' | pal call [OPTIONS] -p <PEER_ADDRESS>

# Write to log
pal call [OPTIONS] -l <LOG_NAME> <CLASS> <METHOD> [args...]
```

### Options

| Option | Description |
|--------|-------------|
| `-p, --to-peer <uuid\|HOST:PORT\|name>` | Target peer by UUID, RPC address, or name |
| `-l, --log <name\|path>` | Read from and write to the same log (name for registry mode, `file:path` for Chronicle, topic name for Kafka) |
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-i, --input-log <name>` | Read responses from this log |
| `-o, --output-log <name>` | Write requests to this log |
| `-r, --rpc-type <type>` | RPC type: `ZMQ_RPC` or `JSON_RPC` |
| `-m, --method <name>` | Method name to call (default: `main`) |
| `-f, --forget-response` | Send without waiting for response (async) |
| `-a, --add-ids` | Auto-generate missing JSON-RPC request IDs |
| `--print-responses <bool>` | Print response messages (default: `true`) |
| `-t, --num-threads <N>` | Number of parallel clients (default: `1`) |
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
pal call -d localhost:2379 -p my-peer com.example.MyClass arg1 arg2

# Call specific method
pal call -d localhost:2379 -p my-peer -m processArgs com.example.MyClass arg1 arg2

# Explicit RPC type
pal call -d localhost:2379 -p my-peer --rpc-type ZMQ_RPC com.example.MyClass
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
# Call main method on peer
pal call -d localhost:2379 -p my-peer com.example.App arg1 arg2

# Call non-main method
pal call -d localhost:2379 -p my-peer -m processData com.example.Processor data1 data2

# Call using peer address instead of name
pal call -p tcp://localhost:5001 com.example.App

# Call using JSON-RPC endpoint
pal call -p ws://localhost:9001 com.example.App
```

#### Writing to Logs (Registry Mode)

```bash
# Write method call to log (async, no response)
pal call -d localhost:2379 -l my-log --forget-response com.example.Worker process

# Write to output log, read response from input log
pal call -d localhost:2379 -i input-log -o output-log com.example.App
```

#### Writing to Logs (Direct Mode)

```bash
# Write to Chronicle log (no PAL directory needed)
pal call -l file:/tmp/my-log --forget-response com.example.Worker process

# Write to Chronicle log (relative path)
pal call -l file:./logs/my-log com.example.App

# Write to Kafka log (with -k option)
pal call -k localhost:29092 -l my-topic --forget-response com.example.App

# Write to Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal call -l my-kafka-topic com.example.Processor data1 data2
```

#### JSON-RPC via Stdin

```bash
# Call method with custom signature
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Math","method":"add","args":[{"type":"int","value":5},{"type":"int","value":3}]}}' | \
  pal call -d localhost:2379 -p ws://localhost:9001

# Construct object
echo '{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.User"}}' | \
  pal call -d localhost:2379 -p ws://localhost:9001

# Get static field
echo '{"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"com.example.Config","field":"VERSION"}}' | \
  pal call -d localhost:2379 -p ws://localhost:9001

# Set static field
echo '{"jsonrpc":"2.0","id":"1","method":"put","params":{"type":"com.example.Config","field":"debugMode","value":true}}' | \
  pal call -d localhost:2379 -p ws://localhost:9001

# Multiple requests (one per line)
cat <<EOF | pal call -d localhost:2379 -p ws://localhost:9001
{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Math","method":"add","args":[{"type":"int","value":5},{"type":"int","value":3}]}}
{"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.example.Math","method":"multiply","args":[{"type":"int","value":5},{"type":"int","value":3}]}}
EOF

# Auto-generate missing IDs
cat requests.jsonl | pal call -d localhost:2379 -p ws://localhost:9001 --add-ids
```

#### Performance Testing

```bash
# Single-threaded call
pal call -d localhost:2379 -p my-peer com.example.Benchmark

# Multi-threaded calls (10 parallel clients)
pal call -d localhost:2379 -p my-peer -t 10 com.example.Benchmark
```

#### Async Operations

```bash
# Fire and forget (write to log, don't wait for response)
pal call -d localhost:2379 -l events --forget-response com.example.Logger logEvent

# Useful for void methods or event notifications
pal call -d localhost:2379 -l notifications -f com.example.Notifier send "Alert!"
```

### RPC Type Selection

The tool automatically infers RPC type based on:

1. Explicit `--rpc-type` option
2. Peer address scheme:
   - `tcp://` → `ZMQ_RPC`
   - `ws://` → `JSON_RPC`
3. Peer's registered endpoints in directory

If a peer supports both RPC types, you must specify `--rpc-type` explicitly.

### Notes

- **CLI mode limitations**: Only works with `static void methodName(String[] args)` signature
- **JSON-RPC flexibility**: Use stdin mode for arbitrary method signatures, constructors, and field access
- **Peer resolution**: Can specify peer by UUID, name, or direct RPC address
- **Async writes**: `--forget-response` only works with logs, not direct peer communication
- **Response printing**: Use `--print-responses false` to suppress output for performance testing
- **Multi-threading**: Each thread creates its own ThinPeer client instance

---

## pal rm - Remove Peers or Logs

Remove peers or logs from the directory and delete their backing storage.

### Synopsis

```bash
pal rm [OPTIONS] -P <PEER...>  # Remove peers
pal rm [OPTIONS] -L <LOG...>   # Remove logs
```

### Options

| Option | Description |
|--------|-------------|
| `-P, --delete-peers` | Delete peers |
| `-L, --delete-logs` | Delete logs |
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (for direct Kafka access without `-d`) |
| `-s, --starting-with` | Treat arguments as prefixes (delete all matching) |
| `-a, --all` | Delete all peers or logs |
| `-f, --force` | Skip confirmation prompts and remove live peers |

### Safety Features

- **Live peer protection**: Cannot remove peers with active leases without `--force`
- **Confirmation prompts**: Asks before deleting multiple items (unless `--force`)
- **Error reporting**: Returns count of errors encountered

### Behavior

**Logs**:

- Unregisters from directory
- Deletes Kafka topic (for Kafka logs)
- Deletes Chronicle queue files (for Chronicle logs)

**Peers**:

- Checks for active lease (is peer alive?)
- Unregisters from directory
- Requires `--force` to remove live peers

### Examples

#### Removing Logs (Registry Mode)

```bash
# Remove single log by name
pal rm -d localhost:2379 -L my-old-log

# Remove log by UUID
pal rm -d localhost:2379 -L 550e8400-e29b-41d4-a716-446655440000

# Remove multiple logs
pal rm -d localhost:2379 -L log1 log2 log3

# Remove all logs with prefix
pal rm -d localhost:2379 -L -s test-log

# Remove all logs (dangerous!)
pal rm -d localhost:2379 -L -a --force
```

#### Removing Logs (Direct Mode)

```bash
# Remove Chronicle log (no PAL directory needed)
pal rm -l file:/tmp/my-chronicle-log

# Remove Chronicle log (relative path)
pal rm -l file:./logs/old-log

# Remove Kafka log (with -k option)
pal rm -k localhost:29092 -l my-kafka-topic

# Remove Kafka log (using KAFKA_SERVERS environment variable)
export KAFKA_SERVERS=localhost:29092
pal rm -l my-old-topic

# Note: Direct mode removes from backing store only
# If log was registered in PAL directory, use registry mode to fully clean up
```

#### Removing Peers

```bash
# Remove peer by UUID
pal rm -d localhost:2379 -P 550e8400-e29b-41d4-a716-446655440000

# Remove peer by name
pal rm -d localhost:2379 -P my-peer

# Remove all peers with prefix
pal rm -d localhost:2379 -P -s test-peer

# Force remove live peer
pal rm -d localhost:2379 -P my-running-peer --force
```

#### Batch Operations

```bash
# Remove all test logs (skipping confirmation)
pal rm -d localhost:2379 -L -s test- --force

# Remove multiple specific peers
pal rm -d localhost:2379 -P peer-alpha peer-beta peer-gamma
```

### Confirmation Prompts

When removing multiple items without `--force`, you'll be prompted:

```
There are 5 logs with UUID '...'. Delete all? (y/n):
```

```
Cannot remove peer my-peer (uuid): peer is alive (has active lease). Use --force to remove anyway.
```

### Notes

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
| `-w, --wal <name\|file:/path>` | **(Required)** WAL to replay from. Use `file:/path` for Chronicle Queue or a topic name for Kafka |
| `-k, --kafka-servers <host:port>` | Kafka bootstrap servers (required for Kafka WAL topics without `-d`) |
| `--divergence-policy <WARN\|HALT\|IGNORE>` | Action on divergence (default: `WARN`) |
| `--replay-threading <ordered\|unordered>` | Thread ordering for multi-threaded replay (default: `ordered`). See [Multi-Threaded Replay](#multi-threaded-replay) |
| `-cp, --classpath <CLASSPATH>` | **(Required)** Classpath for the application |

### Positional Arguments

| Argument | Description |
|----------|-------------|
| `class` | **(Required)** Fully qualified main class to replay |
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

### Multi-Threaded Replay

When the WAL contains operations from multiple threads (e.g., RPC worker threads), replay automatically detects entry-point operations and spawns `ReplayInputInjector` threads to re-inject them. No additional configuration is required beyond ensuring `--wal-incoming-rpc` was enabled during recording (this is the default).

The `--replay-threading` option controls cross-thread ordering:

| Value | Behavior |
|-------|----------|
| `ordered` (default) | Entry-point injection follows WAL-offset ordering. Preserves the recorded execution order across threads. |
| `unordered` | Entry-point injection runs without ordering constraints. Faster, but cross-thread order may differ from the recording. |

```bash
# Replay a multi-threaded RPC service (ordered by default)
pal replay --wal file:/tmp/service-wal -cp target/classes com.example.ServiceMain

# Replay without cross-thread ordering constraints
pal replay --wal file:/tmp/service-wal --replay-threading unordered \
  -cp target/classes com.example.ServiceMain
```

See the [Deterministic Replay Guide](guides/deterministic-replay.md#multi-threaded-replay) for a complete walkthrough.

### Notes

- Replay is **read-only**: no new WAL is written during replay. The recorded WAL is consumed but not modified.
- The application must be compiled with the same AspectJ weaving as during recording. Class version mismatches will surface as operation mismatches.
- **Multi-threaded replay** is supported for applications that receive input on multiple threads (RPC services, web apps, Swing applications). Entry-point operations must be captured in the WAL during recording (`--wal-incoming-rpc`, enabled by default).
- When recording a WAL intended for replay, use `--no-wal-incoming-cli` if you want the WAL to contain only the hot-path operations (excluding the bootstrap `main()` wrapper). This is often the right choice for cleaner replay matching.

---

## pal wal-index - Analyze WAL Structure

Index a WAL and print a structural summary: entry counts, operation/completion pairing, threads, and any structural issues (orphaned or unmatched entries).

### Synopsis

```bash
pal wal-index [OPTIONS] file:/path             # Chronicle Queue
pal wal-index -k <servers> [OPTIONS] <topic>   # Kafka
pal wal-index -d <url> [OPTIONS] <name>        # PalDirectory
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
pal wal-index file:/tmp/my-wal

# Analyze with per-entry detail
pal wal-index --verbose file:/tmp/my-wal

# Analyze a Kafka WAL
pal wal-index -k localhost:29092 my-topic

# Analyze via PAL directory
pal wal-index -d localhost:2379 my-log-name
```

### Notes

- A balanced WAL has equal Operations and Completions counts and zero Issues. Imbalances indicate the application was interrupted mid-execution or the WAL was truncated.
- This command is useful for verifying a WAL before replay, and for understanding the structure of recorded executions.

---

## Common Patterns

### Development Workflow

```bash
# Start development peer
pal run -d localhost:2379 -k localhost:29092 -n dev-peer --zmq-rpc auto -cp target/classes

# List running peers
pal ls -d localhost:2379 -P

# Call method on dev peer
pal call -d localhost:2379 -p dev-peer com.example.TestApp

# View peer's WAL
pal print -d localhost:2379 -l dev-peer-wal -f

# Cleanup when done
pal rm -d localhost:2379 -P dev-peer --force
pal rm -d localhost:2379 -L dev-peer-wal
```

### Testing and Debugging

```bash
# Run test class and capture to log
pal run -d localhost:2379 -k localhost:29092 --wal test-run -cp target/test-classes com.example.MyTest

# Replay and analyze
pal print -d localhost:2379 -l test-run --full --types CLASS_METHOD

# View call tree
pal print -d localhost:2379 -l test-run --tree

# Print specific message and its return value
pal print -d localhost:2379 -l test-run -o 42 --with-return

# Filter to specific class
pal print -d localhost:2379 -l test-run --filter "class=OrderService"

# Cleanup test artifacts
pal rm -d localhost:2379 -L -s test- --force
```

### Distributed System Monitoring

```bash
# List all active peers in cluster
pal ls -d etcd.prod.example.com:2379 -P -l

# Monitor specific peer's output
pal print -d etcd.prod.example.com:2379 -pu <peer-uuid> -f

# View shared log for debugging
pal print -d etcd.prod.example.com:2379 -l shared-events -f --types THROWABLE
```

### Performance Analysis

```bash
# Benchmark with multiple threads
pal call -d localhost:2379 -p bench-peer -t 10 com.example.Benchmark -v

# Analyze log for performance metrics
pal print -d localhost:2379 -l bench-wal --compact | grep -E "method=process"
```

---

## Environment Variables

- `PAL_DIRECTORY` - Default directory URL (etcd endpoint)
- `KAFKA_SERVERS` - Kafka bootstrap servers
- `CHRONICLE_BASE_DIR` - Base directory for Chronicle queues (default: current directory)
- `PAL_CLI_LOGGING_CONFIG` - Custom CLI logging configuration file
- `IN_FLIGHT_TRACKING` - Enable in-flight dispatch tracking for safe intercept activation (default: `true`)
- `DRAIN_TIMEOUT_MS` - Timeout in milliseconds for drain operations during intercept activation (default: `5000`)

---

## Exit Codes

- `0` - Success
- `1` - Invalid arguments or command failure
- `>1` - Number of errors encountered (for `pal rm`)

---

## See Also

- Getting Started guide for `pal run` documentation
- PAL Architecture documentation for concepts (peers, logs, messages)
- Integration tests in `modules/itt/src/test/java/io/quasient/pal/cli/` for more examples
