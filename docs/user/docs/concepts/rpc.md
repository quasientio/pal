# Remote Procedure Calls (RPC)

PAL's RPC system lets you call methods on remote peers as if they were local. No network code, no serialization logic - just normal method calls.

## How RPC Works

When your code calls a method, PAL:

1. Intercepts the call (via AspectJ weaving)
2. Serializes the method name and arguments
3. Sends the message to the target peer
4. Target peer deserializes and executes
5. Result is serialized and returned
6. Your code receives the result

**From your perspective**: It looks like a normal method call.

## RPC Formats

PAL supports two message formats:

### Binary RPC (ZeroMQ)

- **Protocol**: Custom binary format (Colfer)
- **Transport**: ZeroMQ (TCP sockets)
- **Performance**: Very fast (microseconds)
- **Use case**: High-throughput, low-latency communication

Start peer with binary RPC:
```bash
pal run -d localhost:2379 --json-rpc auto \
  -cp app.jar com.example.Service
```

Call with binary RPC:
```bash
pal peer call -d localhost:2379 my-peer \
  com.example.Calculator add 5 3
```

### JSON-RPC (WebSocket)

- **Protocol**: JSON-RPC 2.0
- **Transport**: WebSocket
- **Performance**: Slower but human-readable
- **Use case**: Debugging, cross-language integration, tooling

Start peer with JSON-RPC:
```bash
pal run -d localhost:2379 --json-rpc auto \
  -cp app.jar com.example.Service
```

Call with JSON-RPC:
```bash
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Calculator","method":"add","args":[5,3]}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001
```

## Making RPC Calls

### From CLI

#### Simple Method Call

```bash
pal peer call -d localhost:2379 peer-name \
  com.example.Calculator add 5 3
```

This calls the `main(String[] args)` method by default with the arguments.

#### Specific Method

```bash
pal peer call -d localhost:2379 peer-name \
  -m processData \
  com.example.Processor data1 data2
```

**Note**: CLI mode only works with methods that have `String[]` signature.

### From Java Code

For arbitrary method signatures, use PAL's API or JSON-RPC:

```java
// Using ThinPeer (PAL client)
ThinPeer peer = new ThinPeer(peerUuid, rpcEndpoint);

// Call method
Object result = peer.call("com.example.Calculator", "add",
    new Object[]{5, 3});
```

### Via JSON-RPC

For maximum flexibility:

```bash
# Constructor
echo '{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.User"}}' | \
  pal peer call -d localhost:2379 peer-name

# Method with custom signature
echo '{"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.example.Math","method":"multiply","args":[{"type":"int","value":5},{"type":"int","value":3}]}}' | \
  pal peer call -d localhost:2379 peer-name

# Field access
echo '{"jsonrpc":"2.0","id":"3","method":"get","params":{"type":"com.example.Config","field":"VERSION"}}' | \
  pal peer call -d localhost:2379 peer-name
```

## Peer Addressing

### By Name

```bash
pal peer call -d localhost:2379 my-service \
  com.example.Service doWork
```

PAL looks up "my-service" in the directory and finds its RPC endpoint.

### By UUID

```bash
pal peer call -d localhost:2379 550e8400-e29b-41d4-a716-446655440000 \
  com.example.Service doWork
```

Direct UUID lookup - faster if you already know it.

### By Address

```bash
# Binary RPC
pal peer call tcp://192.168.1.100:5555 \
  com.example.Service doWork

# JSON-RPC
pal peer call ws://192.168.1.100:9001 \
  com.example.Service doWork
```

No directory needed - direct connection.

## RPC vs Log Communication

### Synchronous RPC

**Direct peer-to-peer**:
```bash
pal peer call -d localhost:2379 peer-name \
  com.example.Service process
```

- Blocks until response received
- Low latency
- Peer must be running

### Asynchronous Log

**Fire-and-forget via log**:
```bash
pal log call -d localhost:2379 work-queue --forget-response \
  com.example.Worker process
```

- Doesn't wait for response
- Higher throughput
- Works even if consumer isn't running yet

## Method Call Types

### Static Methods

```bash
pal peer call -d localhost:2379 peer-name \
  com.example.Utils processData arg1 arg2
```

Calls: `Utils.processData(String[] args)`

### Instance Methods

Requires object creation first:

```json
// 1. Create object
{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.Calculator"}}

// Response includes ObjectRef UUID
{"jsonrpc":"2.0","id":"1","result":"550e8400-e29b..."}

// 2. Call instance method
{"jsonrpc":"2.0","id":"2","method":"call","params":{"target":"550e8400-e29b...","method":"add","args":[5,3]}}
```

### Constructors

```json
{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.User","args":[{"type":"java.lang.String","value":"john"}]}}
```

Returns an ObjectRef that can be used in subsequent calls.

### Field Access

```json
// Read field
{"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"com.example.Config","field":"version"}}

// Write field
{"jsonrpc":"2.0","id":"2","method":"put","params":{"type":"com.example.Config","field":"debugMode","value":true}}
```

## Error Handling

### Exceptions

If the remote method throws an exception:

```bash
$ pal peer call -d localhost:2379 peer-name \
    com.example.Calculator divide 10 0

Error: java.lang.ArithmeticException: / by zero
  at com.example.Calculator.divide(Calculator.java:42)
  ...
```

The exception is serialized, sent back, and re-thrown on the caller side.

### Timeouts

RPC timeouts are configured at the transport level, not via a CLI flag. ZeroMQ and WebSocket transports have their own timeout settings. See transport configuration documentation for details.

### Peer Not Found

```bash
$ pal peer call -d localhost:2379 missing-peer \
    com.example.Service process

Error: Peer not found: missing-peer
```

Check with `pal peer ls` to see available peers.

## Performance Considerations

### Binary vs JSON

| Aspect | Binary | JSON |
|--------|--------|------|
| Latency | ~100μs | ~500μs |
| Throughput | ~100K msgs/sec | ~20K msgs/sec |
| Readability | No | Yes |
| Debugging | Harder | Easier |

**Recommendation**: Use binary for production, JSON for debugging.

### Connection Pooling

PAL reuses connections:

- Single ZeroMQ context shared across sockets
- WebSocket connections kept alive
- No need to manage connections manually

### Batching

For bulk operations, use log-based communication:

```bash
# Write many messages to log
for i in {1..1000}; do
  pal log call work-queue --forget-response \
    com.example.Worker process $i
done
```

Consumer processes in batch.

## Security

### Network Security

- Binary RPC uses raw TCP (consider VPN/firewall)
- JSON-RPC uses WebSocket (can add TLS)
- No built-in authentication (use network-level security)

### Method Access Control

PAL provides an [RPC policy system](rpc-policy.md) that controls which operations remote callers can invoke. Policies are defined in YAML files and support:

- Ant-style pattern matching for classes and methods
- Built-in safety presets that block dangerous operations (`System.exit`, `Runtime.exec`, etc.)
- Channel-scoped rules (different policies for ZMQ vs WebSocket)
- Member category filtering (methods, constructors, fields)

```bash
# Quick setup: block dangerous operations
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy-preset deny-unsafe,deny-jdk-internals \
  -cp app.jar com.example.Main
```

See [RPC Policy](rpc-policy.md) for the full guide.

## Common Patterns

### Request-Reply Service

**Server**:
```bash
pal run -d etcd:2379 --json-rpc auto -n calculator \
  -cp calc.jar com.example.Calculator
```

**Client**:
```bash
while true; do
  pal peer call -d etcd:2379 calculator \
    com.example.Calculator add $RANDOM $RANDOM
  sleep 1
done
```

### Async Worker Queue

**Producer**:
```bash
pal log call -d etcd:2379 work-queue --forget-response \
  com.example.Worker process data
```

**Consumer**:
```bash
pal run -k kafka:9092 --source-log work-queue \
  -cp worker.jar com.example.Worker
```

### Load Balancing

Start multiple instances with same name:
```bash
# Terminal 1
pal run -d etcd:2379 --json-rpc auto -n worker \
  -cp worker.jar com.example.Worker

# Terminal 2
pal run -d etcd:2379 --json-rpc auto -n worker \
  -cp worker.jar com.example.Worker
```

PAL directory maintains all instances. (Note: Currently no automatic load balancing - client gets first match).

## Debugging RPC

### Trace Messages

Enable verbose output:
```bash
pal peer call -d localhost:2379 peer-name -v \
  com.example.Service process
```

### Print RPC Traffic

Subscribe to peer's message stream:
```bash
pal peer print -d localhost:2379 peer-uuid -f
```

See all messages sent and received in real-time.

### Check Connectivity

```bash
# Verify peer is running
pal peer ls -d localhost:2379 -l

# Check RPC endpoint
# Look for ZMQ-RPC or JSON-RPC column
```

## Limitations

### CLI Mode

Only supports methods with `String[]` parameter:

```java
// Works
public static void main(String[] args) { }
public static void process(String[] args) { }

// Doesn't work from CLI
public static int add(int a, int b) { }
```

Use JSON-RPC for arbitrary signatures.

### Object Serialization

Complex objects must be serializable. Primitives, Strings, and arrays work automatically.

For custom objects, PAL creates ObjectRefs (UUIDs) instead of copying data.

## Further Reading

- [Peers and Logs](peers-and-logs.md) - Understanding peers
- [Interception](interception.md) - Intercepting RPC calls
- [CLI Reference](../cli-reference.md) - Complete `pal peer call` and `pal log call` documentation
- [Distributed Application Guide](../guides/distributed-application.md) - Building with RPC
