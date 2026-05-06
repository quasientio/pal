# Log Backends

PAL supports two log backends: **Chronicle Queue** (local, memory-mapped files) and **Kafka** (distributed, networked). The choice depends on whether you need a local single-machine log or a distributed log that multiple peers and external systems can consume.

## Chronicle Queue

Chronicle Queue is a memory-mapped, file-based message queue that lives on the local filesystem. It is exceptionally fast and requires no infrastructure to run.

### When to Use Chronicle

- **Local development**: No Kafka cluster needed
- **Single-machine applications**: Everything runs on one computer
- **High-throughput workloads**: Many messages per second with minimal overhead
- **Debugging**: Capture and replay execution locally
- **Benchmarking**: Minimal overhead for performance testing

### Chronicle Queue Features

- **Memory-mapped I/O**: Zero-copy reads/writes
- **Ordered**: Messages written sequentially
- **Indexed**: Fast seeking to specific positions
- **Persistent**: Survives restarts
- **No network**: Pure filesystem operations
- **Multi-reader (local)**: Multiple processes on the same host can tail the same queue concurrently

### Using Chronicle Queue

Specify logs with `file:/` prefix:

```bash
# Absolute path
pal run --wal file:/var/lib/pal/my-wal -cp app.jar

# Relative path (relative to current directory)
pal run --wal file:my-wal -cp app.jar

# Both source and WAL
pal run --source-log file:/tmp/input \
  --wal file:/tmp/output \
  -cp app.jar
```

### Chronicle Queue Structure

```
my-wal/
├── 20251101.cq4        # Today's messages
├── 20251031.cq4        # Yesterday's messages
└── metadata.cq4t       # Queue metadata
```

- New file created each day (roll cycle)
- Old files can be deleted when no longer needed
- Each file is memory-mapped for fast access

### Chronicle Queue Operations

**Create a queue** (by writing to it):

```bash
pal run --wal file:/tmp/my-queue -cp app.jar com.example.Main
```

**Read from queue**:

```bash
pal run --source-log file:/tmp/my-queue -cp app.jar
```

**Print queue contents**:

```bash
pal log print file:/tmp/my-queue --json
```

**Delete old queue** (preferred — also cleans up the etcd registration if any):

```bash
pal log rm file:/tmp/my-queue
```

Or remove the directory directly: `rm -rf /tmp/my-queue`.

## Kafka

Kafka is a distributed streaming platform. PAL writes WALs as Kafka topics, making the operation stream available to any Kafka consumer and to the broader Kafka ecosystem.

### Single-Partition Design

PAL writes to and reads from a single partition (partition 0) of each Kafka topic. This guarantees strict total ordering of the operation stream within a topic. Throughput on a single topic is bounded by single-partition performance — to scale aggregate throughput, use multiple topics (typically one WAL per peer) rather than partitioning a single topic.

### When to Use Kafka

- **Distributed systems**: Multiple machines
- **Cross-host consumers**: Peers and tools on other machines reading the same log
- **Pub/Sub**: Broadcast messages to subscribers
- **Persistence across machines**: Replication and durability
- **Integration**: Connect with other Kafka-based systems

### Kafka Features

- **Distributed**: Data replicated across brokers
- **Durable**: Configurable replication factor
- **Pub/Sub**: Multiple consumers per topic
- **Retention**: Configurable (time or size based)

### Using Kafka

Specify Kafka servers and topic names:

```bash
# With Kafka servers
pal run -k localhost:29092 --wal my-wal -cp app.jar

# With directory and Kafka
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-wal -cp app.jar com.example.Service
```

### Kafka Topics

PAL creates Kafka topics automatically:

```bash
# Start peer with WAL
pal run -k localhost:29092 --wal service-wal -cp service.jar

# Topic "service-wal" is created automatically
# Always 1 partition; replication factor 1 by default
```

List Kafka topics:

```bash
kafka-topics.sh --bootstrap-server localhost:29092 --list
```

View messages:

```bash
kafka-console-consumer.sh --bootstrap-server localhost:29092 \
  --topic my-wal --from-beginning
```

## Comparison

| Feature | Chronicle Queue | Kafka |
|---------|----------------|-------|
| **Deployment** | Local files | Distributed cluster |
| **Setup** | Zero config | Requires Kafka installation |
| **Latency** | Very low (memory-mapped, no network) | Higher (network round-trip) |
| **Throughput** | Very high (single-machine ceiling) | Per-topic single-partition ceiling; aggregate scales with more topics |
| **Consumers** | Multiple, single-host only | Multiple, networked |
| **Durability** | Local disk | Replicated across brokers |
| **Scalability** | Vertical (faster disk) | Horizontal (more brokers) |
| **Use case** | Dev, testing, single-machine | Production, distributed systems |
| **Dependencies** | None | Kafka cluster |

## Seeking and Offsets

### Chronicle Queue Index

```bash
# Start from specific index
pal run --source-log file:/tmp/log --start-offset 1000 -cp app.jar
```

Reads from index 1000 onward.

### Kafka Offset

```bash
# Start from specific Kafka offset
pal run -k localhost:29092 --source-log my-log \
  --start-offset 1000 -cp app.jar
```

For special offset values (e.g., reading from the beginning or from the latest message), see the [CLI Reference](../cli-reference.md).

## Printing Logs

### Chronicle Queue

```bash
# Print all messages
pal log print file:/tmp/my-log

# Print specific offset
pal log print file:/tmp/my-log -o 100

# Follow (like tail -f)
pal log print file:/tmp/my-log -f
```

### Kafka

```bash
# Print all messages
pal log print -d localhost:2379 my-log

# Print with full details
pal log print -d localhost:2379 my-log --full

# Follow new messages
pal log print -d localhost:2379 my-log -f
```

## Managing & Retention

### Chronicle Queue Cleanup

Prefer `pal log rm` to delete a queue — it removes the queue directory and also cleans up any etcd registration:

```bash
pal log rm file:/tmp/old-queue
```

For surgical filesystem operations (e.g., deleting a specific day's roll file while keeping the queue active), use direct commands:

```bash
# Remove one day's roll file — queue continues with remaining files
rm /tmp/my-queue/20251030.cq4

# Or remove the whole directory directly (bypasses PAL)
rm -rf /tmp/old-queue/
```

### Kafka Topic Management

Strongly prefer `pal log rm` to delete a Kafka log — it removes the Kafka topic *and* deletes the corresponding `LogInfo` entry in etcd:

```bash
pal log rm -d localhost:2379 old-log
```

This matters when the log was originally registered in etcd (created by a peer running with `-d` or `PAL_DIRECTORY` env var) and etcd is currently reachable. In that case, deleting only the Kafka topic with `kafka-topics.sh` leaves a stale `LogInfo` entry that can mislead later peers and CLI commands looking up the log by name. The direct path is appropriate only when the log was never registered in etcd, or when etcd is currently unreachable:

```bash
kafka-topics.sh --bootstrap-server localhost:29092 \
  --delete --topic old-topic
```

### Log Retention

**Chronicle**: Manual — delete old daily-rolled files when no longer needed.

**Kafka**: Automatic — configure a retention policy in Kafka:

```properties
# In Kafka server.properties
log.retention.hours=168  # 7 days
log.retention.bytes=1073741824  # 1GB
```

## Mixed Scenarios

### Develop with Chronicle, Deploy with Kafka

The same application code can run against either backend by changing CLI flags:

```bash
# Development
pal run --wal file:/tmp/dev-log \
  -cp build/classes/java/main com.example.Service

# Production
pal run -d etcd:2379 -k kafka:9092 --wal service-wal \
  -cp service.jar com.example.Service
```

For peers that bridge the two backends (read from one, write to the other), see the **Log Transformer** pattern in [Peers and Logs](peers-and-logs.md#log-transformer).

## Kafka Ecosystem Integration

Because Kafka-backed WALs are standard Kafka topics, they participate in the Kafka ecosystem. The WAL becomes an open integration surface for your operations data, useful well beyond PAL's built-in features.

Example use cases:

- **Kafka Streams**: Real-time aggregation or transformation of the operation stream
- **Kafka Connect**: Sink operations to Elasticsearch for full-text search, to a database for analytics, or to S3 for archival
- **Custom consumers**: Build monitoring dashboards, anomaly detection, or compliance reporting on top of the operation stream
- **ksqlDB**: Run SQL queries over the live operation stream for ad-hoc analysis

## Performance Tuning

### Chronicle Queue

**Faster disk = faster performance**:

- Use SSD for queue directory
- Avoid network filesystems (NFS, SMB)
- Ensure sufficient disk space

**Memory-mapped benefits**:

- OS manages caching (uses page cache)
- No explicit buffer management needed
- Zero-copy from disk to memory

### Kafka

**Single partition per topic**:

PAL uses a single partition per Kafka topic (see [Single-Partition Design](#single-partition-design)). To scale aggregate throughput, add more peers — each with its own WAL topic — rather than adding partitions to a single topic.

**Producer batching**:

- Kafka batches messages automatically
- Higher throughput, slightly higher latency

**Consumer groups**:

With a single partition per topic, only one consumer in a consumer group can actively read at a time. Consumer groups remain useful for failover — if the active consumer dies, another in the group takes over from the last committed offset — but they do not provide parallel reads for a PAL log.

## Troubleshooting

### Chronicle Queue Errors

**"Chronicle queue does not exist"**:

```bash
# Create it first by writing to it
pal run --wal file:/tmp/new-queue -cp app.jar
```

**"Permission denied"**:

```bash
# Check permissions
ls -la /tmp/my-queue
chmod -R u+rw /tmp/my-queue
```

**"Disk full"**:

```bash
# Check disk space
df -h

# Delete old queues (preferred — also cleans up etcd if registered)
pal log rm file:/tmp/old-queue

# Or remove directly
rm -rf /tmp/old-queues/
```

### Kafka Errors

**`ERROR_NO_KAFKA_SERVERS_GIVEN`** (exit code 6):

```bash
# Provide Kafka servers
export PAL_KAFKA_SERVERS="localhost:29092"
# Or use CLI option
pal run -k localhost:29092 ...
```

**`ERROR_INITIALIZING_LOGS`** (exit code 7) — typically Kafka unreachable or topic-init failure:

```bash
# Check Kafka is running
docker ps | grep kafka
# Or test broker connectivity
kafka-broker-api-versions.sh --bootstrap-server localhost:29092
```

**"Topic not found"**:
PAL creates topics automatically. If error persists, create manually:

```bash
kafka-topics.sh --bootstrap-server localhost:29092 \
  --create --topic my-log
```

## Further Reading

- [Peers and Logs](peers-and-logs.md) — Conceptual overview, WAL vs Source Log roles, log transformer pattern
- [Recording Scope](recording-scope.md) — Filter what gets recorded to the WAL
- [Local Development Guide](../guides/local-development.md) — Using Chronicle locally
- [Distributed Application Guide](../guides/distributed-application.md) — Using Kafka in production
