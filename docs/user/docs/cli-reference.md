# CLI Reference

PAL provides a command-line interface for managing peers, logs, and remote procedure calls. This reference documents all CLI subcommands except `pal run` (covered in the Getting Started guide).

## Overview

The PAL CLI uses a subcommand structure:

```bash
pal <subcommand> [OPTIONS] [ARGUMENTS]
```

Common options across all subcommands:

- `-d, --dir <URL>` - PAL directory URL (etcd endpoint, e.g., `localhost:2379`)
- `-k, --kafka-servers <host:port>` - Kafka bootstrap servers (for direct access)
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

**Usage**: Specify `-d/--directory` option or set `PAL_DIRECTORY` environment variable.

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

## pal ls - List Peers and Logs

List registered peers and logs in the directory.

### Synopsis

```bash
pal ls [OPTIONS]
```

### Options

| Option | Description |
|--------|-------------|
| `-P, --peers` | List only peers |
| `-L, --logs` | List only logs |
| `-l, --long` | Use long listing format with detailed information |
| `-S, --sort-by-size` | Sort logs by size, largest first |
| `-c, --sort-by-ctime` | Sort by creation/uptime, newest first |
| `-r, --reverse` | Reverse the sorting order |

### Behavior

- **No flags**: Lists both peers and logs
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

### Notes

- Lists both Kafka and Chronicle logs
- Chronicle logs use `file:` prefix in the directory but are displayed without it
- Long format truncates long values with ".." to fit columns
- Logs must exist in their backing store (Kafka or Chronicle) to be displayed

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
| `--output-format <FORMAT>` | Output format: `FULL`, `JSON`, `COMPACT` (default: `COMPACT`) |
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
pal print -d localhost:2379 -l my-chronicle-log --output-format FULL

# Print message at specific offset
pal print -d localhost:2379 -l my-log -o 100

# Wait for and print message at future offset (follow mode)
pal print -d localhost:2379 -l my-log -o 999 -f

# Follow new messages (like tail -f)
pal print -d localhost:2379 -l my-log -f

# Print only method call messages
pal print -d localhost:2379 -l my-log --types CLASS_METHOD,INSTANCE_METHOD

# Print messages in JSON format
pal print -d localhost:2379 -l my-log --output-format JSON

# Print messages from specific peer
pal print -d localhost:2379 -l my-log -fp <peer-uuid>

# Verbose output with diagnostics
pal print -d localhost:2379 -l my-log -v
```

#### Reading from Logs (Direct Mode)

```bash
# Print from Chronicle log (absolute path)
pal print -l file:/tmp/my-chronicle-log --output-format FULL

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
  - When `-o` is specified, all other filters are ignored
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
pal call [OPTIONS] -p <PEER> -m <METHOD> <CLASS> [args...]

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
pal call -d localhost:2379 -p tcp://localhost:5001 com.example.App

# Call using JSON-RPC endpoint
pal call -d localhost:2379 -p ws://localhost:9001 com.example.App
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
| `-l, --log <name\|path>` | Delete a single log (alternative to `-L`, supports direct mode paths like `file:path`) |
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
pal print -d localhost:2379 -l test-run --output-format FULL --types CLASS_METHOD

# Print specific message
pal print -d localhost:2379 -l test-run -o 42

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
pal print -d localhost:2379 -l bench-wal --output-format COMPACT | grep -E "method=process"
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
