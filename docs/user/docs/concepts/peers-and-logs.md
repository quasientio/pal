# Peers and Logs

Peers run your code; logs capture what they do. Understanding both is fundamental to using PAL effectively.

## What is a Peer?

A **peer** is a PAL runtime instance that executes your Java application code. Beyond running the code, a peer can communicate with other peers via RPC, log every operation it performs to a durable stream, and have its behavior modified at runtime through interception.

### Peer Characteristics

Every peer has:

- **Unique ID**: A UUID that identifies it globally
- **Optional Name**: A human-readable name (e.g., "my-service"). Peer names must be unique within a directory namespace — registering a second peer with the same name fails with `DuplicatePeerNameException`.
- **Endpoints**: Addresses for RPC communication (ZeroMQ for binary RPC, WebSocket for JSON-RPC)
- **Logs**: Optional Kafka topics or Chronicle queues used as the peer's source log and write-ahead log
- **Directory Registration**: Listed in etcd for service discovery

### Running a Peer

Basic peer:

```bash
pal run -cp myapp.jar com.example.Main
```

Peer with RPC:

```bash
pal run --json-rpc auto -cp myapp.jar com.example.Main
```

Named peer in directory:

```bash
pal run -d localhost:2379 -n my-peer --json-rpc auto \
  -cp myapp.jar com.example.Main
```

## What is a Log?

A **log** is a durable, ordered stream of messages. In PAL, every method call, field access, and constructor invocation can become a message written to a log.

PAL distinguishes two roles a log can play for a peer:

- **Write-Ahead Log (WAL)** — the log a peer **writes to**. Each operation the peer executes is appended to its WAL alongside its return value.
- **Source Log** — the log a peer **reads from**. Messages in the source log can be re-dispatched into the runtime, driving the peer's execution.

A single log can serve as both roles at once (a peer reading from and writing to the same log), or two different logs can be used (for example, reading from Kafka and writing to Chronicle for local analysis).

### Log Backends

PAL supports two log backends — see [Log Backends](logs.md) for full details:

| Feature | Chronicle Queue | Kafka |
|---------|----------------|-------|
| **Performance** | Ultra-fast (nanoseconds) | Fast (milliseconds) |
| **Location** | Local files | Distributed cluster |
| **Use Case** | Local dev, single machine | Production, multi-peer |
| **Setup** | Zero config | Requires Kafka cluster |

### Why Logs Matter

Logs enable:

- **Replay** — re-execute past operations from any offset, useful for debugging or rebuilding state
- **Audit** — operations are captured with their arguments and return values, providing a record of what executed without manual logging in your application code
- **Multiple readers** — Chronicle queues and Kafka topics both allow several consumers to read the same log stream concurrently
- **Integration with the broader ecosystem** — when backed by Kafka, the WAL is just a Kafka topic, so Kafka Streams, ksqlDB, Kafka Connect, and any Kafka consumer can process the operation stream for analytics, indexing, materialized views, anomaly detection, archival, or non-Java integrations — without PAL having to handle these directly

## Directory Service (etcd)

The directory is a distributed key-value store (etcd) where peers and logs register themselves so other peers can discover them.

### What's Stored

- **Peers**: UUID, name, endpoints, uptime
- **Logs**: Name, type (Kafka/Chronicle), size, offsets
- **Intercepts**: Callback registrations

### Listing Peers and Logs

```bash
# List all peers
pal peer ls -d localhost:2379

# List with details
pal peer ls -d localhost:2379 -l

# List logs
pal log ls -d localhost:2379 -l
```

### Finding a Peer

By name:

```bash
pal peer call -d localhost:2379 my-service \
  com.example.Service doWork
```

By UUID:

```bash
pal peer call -d localhost:2379 550e8400-e29b-41d4-a716-446655440000 \
  com.example.Service doWork
```

## Peer Configurations

Peers are configured by combining CLI flags. The most common configurations:

### Standalone

No directory, no logs — just run code:

```bash
pal run -cp app.jar com.example.Main
```

Good for quick local tests and running JAR files without infrastructure.

### Local with Logs (Chronicle)

Persist operations to a local Chronicle queue, no Kafka required:

```bash
pal run --wal file:/tmp/dev --json-rpc auto \
  -cp build/classes/java/main com.example.Dev
```

Replay later by pointing a peer at it as a source log:

```bash
pal run --source-log file:/tmp/dev -cp app.jar
```

### Distributed Service

Full distributed mode — service discovery, RPC, distributed logging:

```bash
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-wal --json-rpc auto -n my-service \
  -cp app.jar com.example.Service
```

### Worker

Consumes messages from a log:

```bash
pal run -k kafka:9092 \
  --source-log work-queue \
  -cp worker.jar com.example.Worker
```

### Log Transformer

Reads from one log and writes to another. The two backends can be combined in either direction — Kafka source → Chronicle WAL to capture a production stream for local analysis, or Chronicle source → Kafka WAL to publish a locally-recorded execution to a Kafka topic:

```bash
# Kafka → Chronicle (local analysis)
pal run -k kafka:9092 \
  --source-log prod-log \
  --wal file:/tmp/local-copy \
  -cp transform.jar

# Chronicle → Kafka (publish local execution)
pal run -k kafka:9092 \
  --source-log file:/tmp/local-log \
  --wal kafka-topic \
  -cp transform.jar
```

### Interceptable

Allow other peers to register intercepts that fire when this peer executes matching operations:

```bash
pal run -d localhost:2379 --interceptable -cp app.jar com.example.App
```

### Monitor

Observes other peers via interception:

```bash
pal run -d localhost:2379 --zmq-rpc auto -n monitor \
  -cp monitor.jar com.example.Monitor
```
Then run `pal intercept apply` to register intercepts that trigger callbacks in "monitor".
See [Intercept Bundles](interception.md#intercept-bundles) for details.

## Controlling What Goes to the WAL

By default, when `--wal` is set the peer records every quantized operation it executes — operations from its own application code, incoming RPC calls from other peers, and the initial `main()` bootstrap call. Three flags refine that:

| Flag | Default | What it controls |
|------|---------|------------------|
| `--wal-incoming-rpc` | On | Whether incoming RPC calls (ZMQ, WebSocket) are recorded |
| `--wal-incoming-cli` | On | Whether the `main()` bootstrap call is recorded |
| `--wal-all-incoming-rpc` | Off | Also record RPC calls that arrive via source-log replay (LOG_RPC channel). Implies `--wal-incoming-rpc` |

Examples:

```bash
# Default: incoming socket RPC (ZMQ/WebSocket) and main() are recorded; LOG_RPC is not
pal run --wal file:/tmp/my-wal -cp app.jar com.example.Main

# Don't record incoming RPC (capture only locally-initiated operations)
pal run --wal file:/tmp/my-wal --no-wal-incoming-rpc --json-rpc auto \
  -cp app.jar com.example.Main

# Don't record the bootstrap main() call
pal run --wal file:/tmp/my-wal --no-wal-incoming-cli -cp app.jar com.example.Main
```

For finer-grained filtering by class, package, or member, see [Recording Scope](recording-scope.md). For full flag details, see the [CLI Reference](../cli-reference.md#wal-incoming-message-flags).

## Tapping the Peer's PUB Stream

Each peer publishes the messages it processes on a ZeroMQ PUB socket — the same stream that gets written to the WAL. You can tap that stream live:

```bash
pal peer print -d localhost:2379 peer-uuid -f
```

This shows every quantized operation the peer is processing in real time: intercepted method calls, constructor invocations, field reads/writes, incoming RPC, and internal traffic. It is the live counterpart of `pal log print`, which reads from a recorded WAL after the fact. On a busy peer the stream can be very noisy; the PUB interface is intentionally lightly documented for now and may evolve.

## Peer Lifecycle

### Startup

1. Parse CLI arguments
2. Initialize logging
3. Connect to directory (if specified)
4. Start RPC servers (if specified)
5. Open logs (if specified)
6. Execute main class (if specified)

### Running

- Accepts RPC calls
- Writes to WAL
- Reads from source log
- Processes intercepts

### Shutdown

1. Stop accepting new requests
2. Finish in-flight operations
3. Unregister from directory
4. Close logs and sockets

Graceful shutdown is triggered by Ctrl-C or SIGTERM.

## Best Practices

### Naming Peers

Use descriptive names:

```bash
pal run -n user-service ...
pal run -n order-processor ...
pal run -n metrics-collector ...
```

Avoid generic names like "peer1", "test", "app".

### Log Management

Log retention, cleanup, and roll cycles differ between Chronicle and Kafka. See [Log Backends](logs.md) for guidance specific to each backend.

### Directory Cleanup

Remove stale peers:

```bash
pal peer rm -d localhost:2379 old-peer
```

Remove old logs:

```bash
pal log rm -d localhost:2379 old-log
```

## Troubleshooting

### Peer Won't Start

**Error**: `ERROR_UNREACHABLE_ETCD` (exit code 14)

**Fix**: Ensure etcd is running:

```bash
curl http://localhost:2379/health
```

**Error**: `ERROR_NO_KAFKA_SERVERS_GIVEN` (exit code 6) or `ERROR_INITIALIZING_LOGS` (exit code 7)

**Fix**: Provide Kafka servers and ensure the broker is reachable:

```bash
docker ps | grep kafka
pal run -k localhost:29092 ...
```

### Can't Find Peer

**Issue**: `pal peer call` says "peer not found"

**Debug**:

```bash
pal peer ls -d localhost:2379 -l
```

If the peer isn't listed, it may have crashed or its lease may have expired.

### Log Errors

**Error**: "Chronicle queue does not exist"

**Fix**: Create the queue first by writing to it:

```bash
pal run --wal file:/tmp/my-log -cp app.jar
```

## Further Reading

- [Remote Procedure Calls](rpc.md) — How to make RPC calls
- [Log Backends](logs.md) — Chronicle vs Kafka details and management
- [Interception](interception.md) — Dynamic callback injection
- [Recording Scope](recording-scope.md) — Filter what gets recorded to the WAL
- [CLI Reference](../cli-reference.md) — Complete command reference
