# Log Backends

PAL supports two log backends: **Chronicle Queue** (local, ultra-fast) and **Kafka** (distributed, networked). Choosing the right one depends on your use case.

## Chronicle Queue

- **What**: Memory-mapped file-based message queue
- **Where**: Local filesystem
- **Speed**: Ultra-fast (nanoseconds)
- **Use for**: Local development, single-machine applications, high-throughput scenarios

### When to Use Chronicle

- **Local development**: No Kafka cluster needed
- **Single-machine applications**: Everything runs on one computer
- **High-throughput testing**: Millions of messages per second
- **Debugging**: Capture and replay execution locally
- **Benchmarking**: Minimal overhead for performance testing

### Chronicle Queue Features

- **Memory-mapped I/O**: Zero-copy reads/writes
- **Ordered**: Messages written sequentially
- **Indexed**: Fast seeking to specific positions
- **Persistent**: Survives restarts
- **No network**: Pure filesystem operations

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
pal print -l file:/tmp/my-queue --full
```

**Delete old queue**:
```bash
rm -rf /tmp/my-queue
```

## Kafka

- **What**: Distributed streaming platform
- **Where**: Kafka cluster (multiple servers)
- **Speed**: Fast (milliseconds)
- **Use for**: Distributed systems, multi-peer communication, production deployments

### When to Use Kafka

- **Distributed systems**: Multiple machines
- **Multiple consumers**: Many peers reading same log
- **Pub/sub**: Broadcast messages to subscribers
- **Persistence across machines**: Replication and durability
- **Integration**: Connect with other Kafka-based systems
- **Scalability**: Horizontal scaling with partitions

### Kafka Features

- **Distributed**: Data replicated across brokers
- **Scalable**: Add partitions for parallelism
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
  --wal my-wal --json-rpc auto \
  -cp app.jar com.example.Service
```

### Kafka Topics

PAL creates Kafka topics automatically:

```bash
# Start peer with WAL
pal run -k localhost:29092 --wal service-wal -cp service.jar

# Topic "service-wal" is created automatically
# Default: 1 partition, replication factor 1
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
| **Latency** | Sub-microsecond | Milliseconds |
| **Throughput** | 5-10M msgs/sec | 100K-1M msgs/sec |
| **Consumers** | Single machine | Multiple, distributed |
| **Durability** | Local disk | Replicated across brokers |
| **Scalability** | Vertical (faster disk) | Horizontal (more brokers) |
| **Use case** | Dev, testing, single-machine | Production, distributed systems |
| **Dependencies** | None | Kafka cluster |

## Choosing a Backend

### Use Chronicle Queue When:

- ✅ Developing locally
- ✅ Running on single machine
- ✅ Need maximum performance
- ✅ Want zero infrastructure
- ✅ Debugging/replaying execution
- ✅ Benchmarking with minimal overhead

### Use Kafka When:

- ✅ Building distributed system
- ✅ Multiple machines involved
- ✅ Need pub/sub semantics
- ✅ Want data replication
- ✅ Integrating with Kafka ecosystem
- ✅ Running in production

## Mixed Scenarios

### Develop with Chronicle, Deploy with Kafka

```bash
# Development
pal run --wal file:/tmp/dev-log --json-rpc auto \
  -cp target/classes com.example.Service

# Production
pal run -d etcd:2379 -k kafka:9092 \
  --wal service-wal --json-rpc auto \
  -cp service.jar com.example.Service
```

Same application code, different log backend.

### Log Transformation

Read from Kafka, write to Chronicle (or vice versa):

```bash
# Kafka → Chronicle (for local analysis)
pal run -k kafka:9092 \
  --source-log prod-log \
  --wal file:/tmp/local-copy \
  -cp app.jar

# Chronicle → Kafka (publish local execution)
pal run -k kafka:9092 \
  --source-log file:/tmp/local-log \
  --wal kafka-topic \
  -cp app.jar
```

## Log Operations

### Write-Ahead Log (WAL)

What the peer writes:

```bash
pal run --wal file:/tmp/my-wal -cp app.jar
```

All operations executed by this peer are written to the WAL.

### Source Log

What the peer reads:

```bash
pal run --source-log file:/tmp/my-source -cp app.jar
```

Peer replays messages from source log.

### Both

```bash
# Transform: read from one, write to another
pal run --source-log file:/input --wal file:/output -cp app.jar

# Or use shorthand for same log:
pal run -l file:/my-log -cp app.jar
```

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

### Special Offsets

- **0**: Start from beginning
- **-1**: Start from latest (skip old messages)
- **Default**: Start from beginning

## Printing Logs

### Chronicle Queue

```bash
# Print all messages
pal print -l file:/tmp/my-log

# Print specific offset
pal print -l file:/tmp/my-log -o 100

# Follow (like tail -f)
pal print -l file:/tmp/my-log -f
```

### Kafka

```bash
# Print all messages
pal print -d localhost:2379 -l my-log

# Print with full details
pal print -d localhost:2379 -l my-log --full

# Follow new messages
pal print -d localhost:2379 -l my-log -f
```

## Managing Logs

### Chronicle Queue Cleanup

```bash
# Delete old queue files
rm -rf /tmp/old-queue/

# Or delete specific day's file
rm /tmp/my-queue/20251030.cq4
```

Queue continues working with remaining files.

### Kafka Topic Management

```bash
# Delete topic
kafka-topics.sh --bootstrap-server localhost:29092 \
  --delete --topic old-topic

# Or use PAL
pal rm -d localhost:2379 -L old-log
```

### Log Retention

**Chronicle**: Manual - delete old files when done

**Kafka**: Automatic - configure retention policy:
```properties
# In Kafka server.properties
log.retention.hours=168  # 7 days
log.retention.bytes=1073741824  # 1GB
```

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

**Partitions for parallelism**:
```bash
kafka-topics.sh --bootstrap-server localhost:29092 \
  --create --topic my-log --partitions 10
```

**Producer batching**:

- Kafka batches messages automatically
- Higher throughput, slightly higher latency

**Consumer groups**:

- Multiple consumers share partitions
- Parallel processing

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
# Delete old queue files
rm -rf /tmp/old-queues/
```

### Kafka Errors

**"ERROR_NO_KAFKA_SERVERS_GIVEN"**:
```bash
# Provide Kafka servers
export KAFKA_SERVERS="localhost:29092"
# Or use CLI option
pal run -k localhost:29092 ...
```

**"ERROR_UNREACHABLE_KAFKA"**:
```bash
# Check Kafka is running
docker ps | grep kafka
# Or
kafka-broker-api-versions.sh --bootstrap-server localhost:29092
```

**"Topic not found"**:
PAL creates topics automatically. If error persists, create manually:
```bash
kafka-topics.sh --bootstrap-server localhost:29092 \
  --create --topic my-log
```

## Best Practices

### Development

Use Chronicle Queue:
```bash
pal run --wal file:/tmp/dev --json-rpc auto \
  -cp target/classes com.example.Dev
```

Fast iteration, no infrastructure needed.

### Testing

Use Chronicle Queue for unit/integration tests:
```bash
@Before
public void setUp() {
    walPath = Files.createTempDirectory("test-wal");
    peer = startPeer("--wal", "file:" + walPath);
}

@After
public void tearDown() {
    stopPeer(peer);
    deleteDirectory(walPath);
}
```

### Production

Use Kafka with replication:
```bash
# Replication factor 3 for durability
kafka-topics.sh --bootstrap-server kafka:9092 \
  --create --topic prod-log \
  --replication-factor 3 --partitions 10
```

## Further Reading

- [Peers and Logs](peers-and-logs.md) - Understanding log roles
- [RPC](rpc.md) - Log-based messaging vs RPC
- [Local Development Guide](../guides/local-development.md) - Using Chronicle locally
- [Distributed Application Guide](../guides/distributed-application.md) - Using Kafka in production
