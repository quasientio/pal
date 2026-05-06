# Properties Reference

PAL peers read runtime configuration from a properties file at startup. Built-in defaults ship inside the PAL JAR; you can overlay any subset of them via `--properties <path>` (or `PAL_PROPERTIES`) for persistent tuning, or via `-D` flags in `PAL_JAVA_OPTS` for one-off overrides. `-D` only takes effect on keys that already exist in the loaded properties — you cannot introduce new keys that way.

```bash
# File overlay
pal run --properties /etc/pal/tuning.properties --wal my-wal -cp app.jar

# One-off via system property
PAL_JAVA_OPTS="-Dwal.kafka.compression_type=lz4" pal run --wal my-wal -cp app.jar
```

Overlay files only need the properties you want to change:

```properties
wal.kafka.linger_ms=50
wal.kafka.batch_size=256000
pub.batch_size=8192
peer.keepalive.seconds=30
```

See [JVM Configuration](jvm-configuration.md) for heap, GC, and JVM options, and [CLI Reference](cli-reference.md) for command-line flags and environment variables.

## Peer

| Property | Default | Description |
|----------|---------|-------------|
| `peer.keepalive.seconds` | `60` | TTL (in seconds) of this peer's lease in the PAL directory. The peer sends keep-alive heartbeats at roughly half this interval. Lowering the value makes the directory detect crashed peers faster, but increases etcd traffic. |
| `log.threadPoolSize` | `1` | Number of threads in the pool that processes log RPC messages. Increase if log operations become a bottleneck. |

## ZeroMQ Context

These properties configure the process-wide ZeroMQ context shared by all sockets (RPC, PUB/SUB).

| Property | Default | Description |
|----------|---------|-------------|
| `ZMQ_LINGER` | `1000` | Time in milliseconds the context waits for pending outbound messages to be sent when a socket closes. `0` discards unsent messages immediately; `-1` waits indefinitely. |
| `ZMQ_RCVHWM` | `10000` | Default receive high-water mark (max queued inbound messages) for sockets created on this context. |
| `ZMQ_SNDHWM` | `10000` | Default send high-water mark (max queued outbound messages) for sockets created on this context. |

## Kafka Source Log Reader

Properties that control how the peer reads messages from a Kafka source log. These map to standard Kafka consumer settings.

| Property | Default | Description |
|----------|---------|-------------|
| `pollDuration` | `10` | Duration in milliseconds for each `consumer.poll()` call. Lower values reduce latency; higher values improve throughput by batching more records per poll. |
| `enable.auto.commit` | `false` | Whether the Kafka consumer automatically commits offsets. When `false` (recommended), PAL manages offset commits explicitly. |
| `auto.commit.interval.ms` | `500` | Interval in milliseconds between automatic offset commits. Only takes effect when `enable.auto.commit` is `true`. |
| `auto.offset.reset` | `earliest` | Where to start reading when no committed offset exists. `earliest` replays from the beginning; `latest` skips to new messages; `none` throws an error. |
| `session.timeout.ms` | `30000` | Kafka consumer session timeout in milliseconds. If the broker receives no heartbeat within this window, the consumer is considered dead and its partitions are reassigned. |

!!! note
    The key and value deserializers are hard-coded in `KafkaSourceLogReader` (PAL's custom serdes). Overriding them would break message deserialization.

## WAL (Write-Ahead Log)

### Queue

Messages pass through an in-memory MPSC (multiple-producer, single-consumer) queue before being written to the WAL backend. Queue sizes must be **powers of 2**.

| Property | Default | Description |
|----------|---------|-------------|
| `wal.queue.type` | `chunked` | Queue implementation (case-insensitive). `chunked` grows in chunks up to `wal.queue.max` (good for variable load). `growable` grows up to `wal.queue.max` with a single backing array. `fixed` uses a fixed-capacity ring sized at `wal.queue.initial` (lowest per-offer cost). `none` disables the WAL queue. |
| `wal.queue.initial` | `16384` | Initial capacity of the WAL queue (number of message slots). |
| `wal.queue.max` | `1048576` | Maximum capacity of the WAL queue. Applies to `chunked` and `growable` types. |
| `wal.flush_on_close` | `true` | Whether to drain and flush pending messages before shutting down the WAL writer. Set to `false` for faster shutdown at the cost of losing buffered messages. |

### Offset Publisher

| Property | Default | Description |
|----------|---------|-------------|
| `wal.offsets.ring_size` | `65536` | Size of the Disruptor ring buffer used to publish WAL offset events. Must be a power of 2. |

### Chronicle Backend

These properties apply when using a Chronicle Queue WAL (`--wal file:/path`).

| Property | Default | Description |
|----------|---------|-------------|
| `wal.chronicle.roll_cycle` | `TEN_MINUTELY` | How often Chronicle creates a new queue file. Shorter cycles produce more files but allow finer-grained retention. Values: `MINUTELY`, `TEN_MINUTELY`, `HOURLY`, `DAILY`, and [others from Chronicle's `RollCycles`](https://javadoc.io/doc/net.openhft/chronicle-queue). |
| `wal.chronicle.block_size` | `134217728` | Size in bytes of each memory-mapped block (default 128 MB). Larger blocks reduce mapping overhead for high-throughput workloads. |
| `wal.chronicle.sync_every` | `-1` | Sync (fsync) to disk after every N messages. `-1` disables explicit syncing and relies on the OS page cache. Set to a positive integer for stronger durability guarantees at the cost of throughput. |
| `wal.chronicle.index_spacing` | `1000` | Number of messages between index entries. Lower values speed up random lookups but increase index size. |

### Kafka Backend

These properties apply when using a Kafka WAL (`--wal <topic>` with `-k`). They configure the Kafka producer that writes WAL messages.

| Property | Default | Description |
|----------|---------|-------------|
| `wal.kafka.linger_ms` | `25` | Time in milliseconds the producer waits for additional messages before sending a batch. Higher values improve batching at the cost of latency. |
| `wal.kafka.batch_size` | `128000` | Maximum size in bytes of a single produce request batch. |
| `wal.kafka.compression_type` | `zstd` | Compression algorithm for WAL messages. Values: `zstd`, `snappy`, `lz4`, `gzip`, `none`. |
| `wal.kafka.buffer_memory` | `128000000` | Total bytes the producer can use for buffering records waiting to be sent (default ~128 MB). |

## Message Publisher (PUB)

The message publisher broadcasts messages to subscribers over ZeroMQ PUB sockets.

### Queue

Like the WAL queue, publisher messages pass through an MPSC queue. Sizes must be **powers of 2**.

| Property | Default | Description |
|----------|---------|-------------|
| `pub.queue.type` | `chunked` | Queue implementation (case-insensitive): `chunked`, `growable`, or `fixed`. See [WAL Queue](#queue) for the differences. `none` is rejected for the PUB queue. |
| `pub.queue.initial` | `16384` | Initial capacity of the PUB queue. |
| `pub.queue.max` | `1048576` | Maximum capacity of the PUB queue. Applies to `chunked` and `growable` types. |

### Publisher

| Property | Default | Description |
|----------|---------|-------------|
| `pub.spsc_size` | `524288` | Capacity of the internal SPSC (single-producer, single-consumer) ring buffer inside the publisher thread. Must be a power of 2. |
| `pub.batch_size` | `4096` | Number of messages to drain from the queue per flush cycle. Higher values improve throughput; lower values reduce publish latency. |
| `pub.flush_on_close` | `true` | Whether to flush queued messages before shutting down the publisher. |

### ZeroMQ Socket

| Property | Default | Description |
|----------|---------|-------------|
| `pub.zmq.linger` | `0` | Linger time in milliseconds for the PUB socket. `0` discards unsent messages on close (appropriate for PUB/SUB where dropped messages are expected). |
| `pub.zmq.send_timeout` | `0` | Send timeout in milliseconds. `0` means non-blocking sends (return immediately if the HWM is reached). `-1` blocks indefinitely. |
| `pub.zmq.send_hwm` | `10000` | Send high-water mark for the PUB socket. When this many messages are queued for a slow subscriber, the drop policy takes effect. |

### Drop Policy

When the publisher queue approaches capacity, the drop policy determines how to shed load.

| Property | Default | Description |
|----------|---------|-------------|
| `pub.drop.policy` | `drop_old` | What to do when the queue reaches the high-water mark (case-insensitive). `drop_old` discards the oldest messages to make room. `drop_new` drops the incoming message instead. `none` spins until space is available (effectively blocks the producer). |
| `pub.drop.hwm_pct` | `97` | Queue fullness percentage that triggers dropping. At 97%, the publisher starts shedding messages. |
| `pub.drop.keep_pct` | `92` | After dropping, the publisher keeps the newest messages down to this percentage of capacity. Must be less than `pub.drop.hwm_pct`. |

For example, with the defaults: when the queue reaches 97% full, the publisher discards the oldest messages until it is back down to 92% full.
