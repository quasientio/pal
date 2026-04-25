# Peers and Logs

Understanding peers and logs is fundamental to using PAL effectively.

## What is a Peer?

A **peer** is a PAL runtime instance that executes your Java application code. Think of it as a JVM with superpowers: it can communicate with other peers, log all operations, and support dynamic interception.

### Peer Characteristics

Every peer has:

- **Unique ID**: A UUID that identifies it globally
- **Optional Name**: A human-readable name (e.g., "my-service"). **Peer names must be unique** within a directory namespace — no two peers can share the same name.
- **Endpoints**: Addresses for RPC communication (ZeroMQ, WebSocket)
- **Logs**: Associated Kafka topics or Chronicle queues
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

### Why Logs Matter

Logs enable:

- **Replay**: Re-execute past operations for debugging
- **Audit**: Track what happened and when
- **Pub/Sub**: Multiple peers read the same log
- **Recovery**: Restart from last checkpoint

### WAL as Integration Point

When backed by Kafka, the WAL is a standard Kafka topic. This means PAL's operation stream integrates with the Kafka ecosystem: Kafka Streams, ksqlDB, Kafka Connect, and any Kafka consumer can process the WAL for purposes PAL doesn't need to handle directly—analytics, caching, search indexing, custom monitoring dashboards, data lake ingestion, etc. The WAL is not a closed system; it's an open data stream in a widely-adopted format.

### WAL Use Cases Beyond Replay

- **Auditing and compliance**: The WAL is a complete record of operations executed by a peer. For regulated environments, this provides an audit trail without manual logging.
- **Debugging**: `pal log print` with various filters lets you inspect what happened in a running system after the fact, without having added debug logging beforehand.
- **Analytics via Kafka ecosystem**: When using Kafka-backed WALs, the operation stream is available to Kafka Streams, ksqlDB, Kafka Connect, and any Kafka consumer for downstream processing—search indexing, analytics, anomaly detection, etc.
- **Caching and derived state**: Kafka consumers can build materialized views or caches from the operation stream.
- **Cross-system integration**: WAL messages can be consumed by non-Java systems via Kafka, enabling integration without PAL's runtime.

### Log Types

PAL supports two log backends:

| Feature | Chronicle Queue | Kafka |
|---------|----------------|-------|
| **Performance** | Ultra-fast (nanoseconds) | Fast (milliseconds) |
| **Location** | Local files | Distributed cluster |
| **Use Case** | Local dev, single machine | Production, multi-peer |
| **Setup** | Zero config | Requires Kafka cluster |

## Peer Modes

### Standalone Peer (No Directory)

Simplest mode - just run code:

```bash
pal run -cp app.jar com.example.Main
```

No directory registration, no service discovery. Good for:

- Running JAR files locally
- Quick testing
- Non-distributed applications

### Peer with Logs

Add persistence:

```bash
# Chronicle (local)
pal run --wal file:/tmp/my-wal -cp app.jar com.example.Main

# Kafka (distributed)
pal run -k localhost:29092 --wal my-wal -cp app.jar com.example.Main
```

All operations are logged. Can replay later:

```bash
pal run --source-log file:/tmp/my-wal -cp app.jar
```

### Peer with Directory and RPC

Full distributed mode:

```bash
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-wal --json-rpc auto -n my-service \
  -cp app.jar com.example.Service
```

Enables:

- Service discovery (other peers can find this peer by name)
- RPC communication (other peers can call methods)
- Distributed logging (Kafka)

### Peer with Interception

Enable dynamic callbacks:

```bash
pal run -d localhost:2379 --interceptable \
  --json-rpc auto -cp app.jar com.example.Main
```

Now you can register intercepts at runtime to get callbacks before/after method calls.

## Directory Service (etcd)

The directory is a distributed key-value store (etcd) where peers register themselves and discover other peers.

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
pal peer call -d localhost:2379 550e8400-e29b... \
  com.example.Service doWork
```

## Write-Ahead Logs (WAL)

A **WAL** captures all operations executed by a peer before they happen. This enables:

### Replay

Run the log again:
```bash
# Capture execution
pal run --wal file:/tmp/execution -cp app.jar com.example.Main

# Replay it
pal run --source-log file:/tmp/execution -cp app.jar
```

### Debugging

See exactly what happened:
```bash
pal log print file:/tmp/execution --full
```

### Backup and Recovery

If a peer crashes, restart it with its WAL:
```bash
pal run --source-log file:/tmp/peer-wal \
  --start-offset 1000 \
  -cp app.jar
```

Starts from offset 1000 instead of the beginning.

### Controlling What Gets Written to the WAL

By default, the WAL records **everything** the peer does: operations from the peer's own application code, incoming RPC calls from other peers, and the initial `main()` bootstrap call. You can control this with three flags:

```bash
# Default: all incoming messages are logged to WAL
pal run --wal file:/tmp/my-wal -cp app.jar com.example.Main

# Don't log incoming RPC calls to WAL (only locally-initiated operations)
pal run --wal file:/tmp/my-wal --no-wal-incoming-rpc --json-rpc auto \
  -cp app.jar com.example.Main

# Don't log the bootstrap main() call to WAL
pal run --wal file:/tmp/my-wal --no-wal-incoming-cli -cp app.jar com.example.Main
```

| Flag | Default | What it controls |
|------|---------|------------------|
| `--wal-incoming-rpc` | On | Incoming RPC calls (ZMQ, WebSocket) |
| `--wal-incoming-cli` | On | The `main()` bootstrap call |
| `--wal-all-incoming-rpc` | Off | Extends RPC logging to include source log replay |

See the [CLI Reference](../cli-reference.md#pal-run-wal-options) for full details and examples.

## Source Logs vs WALs

- **WAL (Write-Ahead Log)**: What this peer writes
- **Source Log**: What this peer reads

Example - log transformation:
```bash
# Read from Kafka, write to Chronicle
pal run -k localhost:29092 \
  --source-log kafka-input \
  --wal file:/tmp/chronicle-output \
  -cp transform.jar
```

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

Graceful shutdown: Ctrl-C or SIGTERM

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

**Chronicle**: Delete old files when done
```bash
rm -rf /tmp/old-wal/
```

**Kafka**: Set retention policies
```bash
# In Kafka configuration
log.retention.hours=168  # 7 days
```

### Directory Cleanup

Remove stale peers:
```bash
pal peer rm -d localhost:2379 old-peer
```

Remove old logs:
```bash
pal log rm -d localhost:2379 old-log
```

## Common Patterns

### Service Peer

Long-running service that handles RPC calls:
```bash
pal run -d etcd:2379 -k kafka:9092 \
  --wal service-wal --json-rpc auto -n my-service \
  -cp service.jar com.example.Service
```

### Worker Peer

Processes messages from log:
```bash
pal run -k kafka:9092 \
  --source-log work-queue \
  -cp worker.jar com.example.Worker
```

### Development Peer

Local testing with Chronicle:
```bash
pal run --wal file:/tmp/dev --json-rpc auto \
  -cp build/classes/java/main com.example.Dev
```

### Monitoring Peer

Intercepts and observes other peers:
```bash
pal run -d etcd:2379 --interceptable \
  --json-rpc auto -n monitor \
  -cp monitor.jar com.example.Monitor
```

## Troubleshooting

### Peer Won't Start

**Error**: "ERROR_UNREACHABLE_ETCD"

**Fix**: Ensure etcd is running:
```bash
curl http://localhost:2379/health
```

**Error**: "ERROR_UNREACHABLE_KAFKA"

**Fix**: Ensure Kafka is running:
```bash
docker ps | grep kafka
```

### Can't Find Peer

**Issue**: `pal peer call` says "peer not found"

**Debug**:
```bash
# List all peers
pal peer ls -d localhost:2379 -l

# Check if peer is alive (has lease)
```

### Log Errors

**Error**: "Chronicle queue does not exist"

**Fix**: Create log first:
```bash
# Write to it first
pal run --wal file:/tmp/my-log -cp app.jar
```

## Further Reading

- [Remote Procedure Calls](rpc.md) - How to make RPC calls
- [Log Backends](logs.md) - Chronicle vs Kafka details
- [Interception](interception.md) - Dynamic callback injection
- [CLI Reference](../cli-reference.md) - Complete command reference
