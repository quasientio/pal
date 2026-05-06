# Remote Procedure Calls (RPC)

PAL's RPC system enables method invocation across peers. It is the transport for intercept callbacks and is also useful for development workflows, testing, debugging, and operational tooling.

For production inter-service communication needing type safety, schema evolution, or high-throughput optimization, use purpose-built RPC frameworks (gRPC, Thrift) alongside PAL — see [Understanding PAL → What PAL Is Not](../understanding-pal.md#what-pal-is-not) for the full framing.

## How RPC Works

When your code calls a method, PAL:

1. Intercepts the call (via AspectJ weaving).
2. Serializes the method name and arguments.
3. Sends the message to the target peer.
4. Target peer deserializes and executes.
5. Result is serialized and returned.
6. Your code receives the result.

**From your perspective**: It looks like a normal method call.

## RPC Formats

PAL supports two message formats:

### Binary RPC (ZeroMQ)

- **Protocol**: Custom binary format ([Colfer](https://github.com/pascaldekloe/colfer))
- **Transport**: ZeroMQ (TCP sockets)
- **Performance**: Very fast (microseconds)
- **Use case**: High-throughput, low-latency communication, PAL internals (intercept callbacks, WAL, PUB)

Start peer with binary RPC:
```bash
pal run -d localhost:2379 --zmq-rpc auto \
  -cp app.jar com.example.Service
```

Call with binary RPC:
```bash
pal peer call -d localhost:2379 my-peer \
  com.example.Calculator add 5 3
```

For programmatic usage from Java, see [Binary RPC (MessageBuilder)](rpc-binary.md).

### JSON-RPC (WebSocket)

- **Protocol**: JSON-RPC 2.0
- **Transport**: WebSocket
- **Performance**: Slower but human-readable
- **Use case**: Debugging, cross-language integration, tooling

Start peer with JSON-RPC:
```bash
# Random port (auto) — discover with `pal peer ls -l` or check the peer's startup log
pal run -d localhost:2379 --json-rpc auto \
  -cp app.jar com.example.Service

# Fixed port — easier when the call command needs a known address
pal run -d localhost:2379 --json-rpc 9001 \
  -cp app.jar com.example.Service
```

Call with JSON-RPC:
```bash
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Calculator","method":"add","args":[5,3]}}' | \
  pal peer call -d localhost:2379 ws://localhost:9001
```

## Abstraction Layers

PAL provides programmatic APIs for both RPC formats, at varying levels of abstraction.

### JSON-RPC layers

| Level | What you use | Best for |
|-------|-------------|----------|
| **Raw JSON** | Hand-written JSON-RPC 2.0 messages | Cross-language clients, shell scripts, debugging |
| **JsonRpcMessageFactory** | Java factory that builds typed `JsonRpcRequest` objects | Java clients that need full control over individual requests |
| **RpcChain DSL** | Fluent Java API that chains operations and tracks ObjectRefs | Multi-step workflows where objects are created, passed around, and queried |

Each layer produces the same wire-format messages — they differ only in how much bookkeeping the caller does:

- **Raw JSON** — you construct the JSON, manage request IDs, and track ObjectRefs yourself. See [JSON-RPC Reference](rpc-json.md).
- **JsonRpcMessageFactory** — you call Java methods that return `JsonRpcRequest` objects; the factory handles JSON structure and ID generation. See [JsonRpcMessageFactory](rpc-json.md#jsonrpcmessagefactory).
- **RpcChain DSL** — you describe a sequence of operations; the DSL handles ObjectRef resolution, request ordering, and result extraction. See [RpcChain DSL](rpc-chain.md).

### Binary RPC layer

| Level | What you use | Best for |
|-------|-------------|----------|
| **MessageBuilder** | Java factory that builds `ExecMessage` / `ControlMessage` objects | High-performance Java clients, PAL internal communication |

Binary messages are always constructed through `MessageBuilder` — there is no hand-crafted wire format. See [Binary RPC (MessageBuilder)](rpc-binary.md).

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

**Note**: this argument-passing form only works with methods that have a `static void method(String[] args)` signature. For arbitrary signatures, see [Via JSON-RPC](#via-json-rpc) below or [CLI Reference → JSON-RPC Stdin Mode](../cli-reference.md#2-json-rpc-stdin-mode).

### From Java Code

PAL provides Java APIs over both wire formats:

- **Binary (ZeroMQ)**: see [Binary RPC (MessageBuilder)](rpc-binary.md) for `ThinPeer` setup and `MessageBuilder` examples.
- **JSON-RPC**: see [RpcChain DSL](rpc-chain.md) for multi-step workflows, or [JsonRpcMessageFactory](rpc-json.md#jsonrpcmessagefactory) for single requests.

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

Address a peer directly by host and port. This **bypasses etcd / PalDirectory entirely** — no `-d` flag, no name lookup, no UUID resolution. Useful when the directory is unavailable, when you're talking to a peer that runs unregistered, or for the lowest-overhead client path.

```bash
# Binary RPC (ZeroMQ)
pal peer call tcp://192.168.1.100:5555 \
  com.example.Service doWork

# JSON-RPC (WebSocket)
pal peer call ws://192.168.1.100:9001 \
  com.example.Service doWork
```

The same direct addressing is available from Java code by setting `peer.setZmqRpcAddress(...)` or `peer.setJsonrpcAddress(...)` on a `PeerInfo` and passing it to `ThinPeer.withInitialPeer(...)` — see [Binary RPC → Direct connection by address](rpc-binary.md#direct-connection-by-address) and [RpcChain → Direct connection by address](rpc-chain.md#direct-connection-by-address).

## RPC vs Log Communication

PAL offers two transport paths for invoking remote methods: direct peer-to-peer (`pal peer call`) and via a log (`pal log call`). Both can be synchronous or fire-and-forget; the difference is *what* carries the message and *who* must be running.

### Direct peer-to-peer (`pal peer call`)

```bash
pal peer call -d localhost:2379 peer-name \
  com.example.Service process
```

- Always waits for a response (no fire-and-forget mode).
- Lowest latency: a single ZMQ or WebSocket round-trip.
- Target peer must be running and reachable.

### Via log (`pal log call`)

```bash
# Synchronous: send via log, then wait for the response on the log
pal log call -d localhost:2379 work-queue \
  com.example.Worker process

# Fire-and-forget: send via log, do not wait for response
pal log call -d localhost:2379 work-queue --forget-response \
  com.example.Worker process
```

- Synchronous by default — the client polls the log for a response message matching its request ID. Like `pal peer call`, it returns the result.
- With `--forget-response`, the client returns as soon as the message is appended to the log, without waiting for a response.
- Persistent: the request survives even if no consumer is currently running. The first peer to consume the topic will execute it.
- Higher latency than direct RPC because the message and (optionally) the response both travel through the log backend (Kafka or Chronicle).

Use `pal peer call` for low-latency synchronous calls to a known, running peer. Use `pal log call` when the request must be durable, when the consumer may not be running yet, or when you want fire-and-forget semantics.

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

For multi-step workflows where you need to create objects, pass them as arguments, and track references across calls, see the [RpcChain DSL](rpc-chain.md) — a Java API that handles ObjectRef management automatically.

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
| Latency | Microsecond-range | Higher (text parsing overhead) |
| Throughput | Higher | Lower (text parsing, larger messages) |
| Readability | No | Yes |
| Debugging | Harder | Easier |

Concrete numbers will be published with benchmark results.

**Recommendation**: Use binary for the hot path (intercept callbacks, internal traffic) and when performance matters; use JSON-RPC for debugging, tooling, and quickly wiring up cross-language clients. Both wire formats are language-agnostic in principle — Colfer has bindings in many languages and ZeroMQ is broadly portable — but PAL currently ships only a Java client (`ThinPeer`) for binary RPC, so non-Java clients today are easier with JSON-RPC.

### Handler Threads (`--rpc-threads`)

When a peer receives RPC traffic, it dispatches incoming messages on a pool of handler threads. Pool size is configured at peer startup with `--rpc-threads` (default: `1`, env: `PAL_RPC_THREADS`). Increase it when a peer needs to handle concurrent RPC calls — e.g., a service serving multiple clients, or a callback peer driven by intercept callbacks under load:

```bash
# Single thread (default) — RPC calls processed serially
pal run -d localhost:2379 --json-rpc auto -cp app.jar com.example.Service

# 4 handler threads — up to 4 concurrent calls in flight
pal run -d localhost:2379 --json-rpc auto --rpc-threads 4 -cp app.jar com.example.Service
```

The flag applies to both `--zmq-rpc` and `--json-rpc` listeners.

**ZeroMQ fair-queueing:** for the binary path, the ZeroMQ socket distributes incoming messages fairly (round-robin) across the handler threads. This is a built-in ZMQ feature — distinct from typical socket pool patterns where a connection sticks to one handler — and provides automatic intra-peer load balancing across `--rpc-threads` without any application code.

### Thread Affinity (`--fx-thread`)

By default an incoming RPC call runs on whichever handler thread picks it up. For some workloads — notably JavaFX UIs — the call must instead execute on a specific named thread (e.g., the JavaFX Application Thread, which is the only thread allowed to mutate the scene graph). Callers indicate this with a thread-affinity hint on the request, and the peer routes matching calls accordingly.

Enable the JavaFX Application Thread router with `--fx-thread`:

```bash
pal run -d localhost:2379 --json-rpc auto --fx-thread --rpc-threads 2 \
  -jar build/libs/my-javafx-app.jar
```

When `--fx-thread` is set, RPC calls tagged with `fx-thread` affinity are dispatched onto the real JavaFX Application Thread via `Platform.runLater()`; calls without that tag use the regular handler pool. Pair it with `--rpc-threads 2+`: when a UI operation occupies the FX thread for a long time, the extra handler threads keep non-UI RPC traffic flowing instead of starving behind it.

Callers from Java set affinity via the [RpcChain DSL](rpc-chain.md#thread-affinity)'s `.onFxThread()` / `.withThreadAffinity(name)` methods.

### Connection Pooling

PAL reuses connections:

- Single ZeroMQ context shared across sockets
- WebSocket connections kept alive
- No need to manage connections manually

### Batching

For bulk operations, two CLI patterns work well:

**Log-based fire-and-forget** — append many messages to a log; consumer drains them at its own rate:

```bash
for i in {1..1000}; do
  pal log call work-queue --forget-response \
    com.example.Worker process $i
done
```

**Piped JSON-RPC** — pipe a stream of JSON-RPC requests through stdin to `pal peer call` or `pal log call`. The CLI sends each request as it arrives, so a single invocation can carry many operations:

```bash
# One JSON-RPC request per line on stdin
cat <<EOF | pal peer call -d localhost:2379 worker
{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Worker","method":"process","args":[{"type":"int","value":1}]}}
{"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.example.Worker","method":"process","args":[{"type":"int","value":2}]}}
{"jsonrpc":"2.0","id":"3","method":"call","params":{"type":"com.example.Worker","method":"process","args":[{"type":"int","value":3}]}}
EOF
```

See [CLI Reference → JSON-RPC Stdin Mode](../cli-reference.md#2-json-rpc-stdin-mode) for the full stdin protocol.

Consumer processes in batch.

## Security

PAL has two distinct security concerns: **transport security** (who can connect) and **authorization** (what callers can invoke).

### Transport security

Binary RPC runs over raw TCP via ZeroMQ; JSON-RPC runs over WebSocket. PAL does not perform peer authentication or transport encryption itself — restrict access via network-level controls (firewall, VPN, mTLS-terminating proxy in front of WebSocket) before exposing a peer's RPC ports outside a trusted boundary.

### Authorization

Authorization is handled by PAL's [RPC policy system](rpc-policy.md), which controls which operations remote callers can invoke. Policies are defined in YAML and support Ant-style class/member patterns, built-in safety presets (e.g. block `System.exit`, `Runtime.exec`), per-channel rules (ZMQ vs WebSocket), and member-category filtering (methods, constructors, fields).

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

### Scaling: Multiple Workers

PAL enforces unique peer names — at most one peer holds a given name at a time. A second registration with the same name is rejected by the directory (`DuplicatePeerNameException`). To run multiple workers, give each peer a distinct name and distribute calls across them in your client code:

```bash
# Terminal 1
pal run -d etcd:2379 --json-rpc auto -n worker-1 \
  -cp worker.jar com.example.Worker

# Terminal 2
pal run -d etcd:2379 --json-rpc auto -n worker-2 \
  -cp worker.jar com.example.Worker
```

PAL has no built-in load balancer; for automatic distribution, prefer the log-based "Async Worker Queue" pattern above — multiple consumer peers can share a single Kafka topic without coordination.

## Debugging RPC

### Trace Messages

Enable verbose output:
```bash
pal peer call -d localhost:2379 peer-name -v \
  com.example.Service process
```

### Check Connectivity

```bash
# Verify peer is running
pal peer ls -d localhost:2379 -l

# Check RPC endpoint
# Look for ZMQ-RPC or JSON-RPC column
```

## Limitations

### CLI args mode is `String[]`-only

`pal peer call` and `pal log call` have two modes. The default **args mode** — positional arguments after the class name — only invokes methods with the signature `static void method(String[] args)`:

```java
// Works in args mode
public static void main(String[] args) { }
public static void process(String[] args) { }

// Doesn't work in args mode
public static int add(int a, int b) { }
```

For arbitrary signatures (any return type, any parameters, constructors, field access), use **JSON-RPC stdin mode** by piping a JSON-RPC request — `pal peer call` and `pal log call` both accept this. See [CLI Reference → JSON-RPC Stdin Mode](../cli-reference.md#2-json-rpc-stdin-mode).

### Object Serialization

PAL serializes RPC arguments and return values by value when they are:

- **Simple types**: `null`, primitives, primitive wrappers (`Integer`, `Long`, etc.), and `String`.
- **Arrays** of any element type, up to 1000 elements.
- **Collections and Maps** with JSON-serializable contents, up to 1000 elements/entries.

Anything else — custom POJOs, framework objects, arbitrary object graphs — is not serialized by value. Instead, PAL returns an integer `ObjectRef` identifying the object on the remote peer; subsequent calls reference it by ref without copying data. Oversized arrays or collections (more than 1000 elements) throw `NonWrappableObjectException` unless an ObjectRef is supplied for the value.

ObjectRefs are scoped to the peer that owns the object — a ref returned by peer B is meaningful only when sent back to B in a subsequent call. You cannot use that ref to reference the object on a third peer.

### Reflection-Based Dispatch

PAL's RPC uses reflection to invoke methods on the target peer. There is no compile-time type checking of remote calls—if a method signature is wrong, the error occurs at runtime, not at compile time.

### No Schema Evolution

If a method signature changes (parameters added, types changed, method renamed), all callers must be updated manually. PAL has no schema versioning or compatibility checking — caller and callee must agree on signatures out of band.

### No Built-In Resilience Patterns

PAL's RPC does not include retry, circuit breaking, or automatic load balancing. These are application-layer concerns; for traffic shaping, distribute calls in your client code or front the peer with a log-based queue (see "Async Worker Queue" above).

## Further Reading

- [Binary RPC (MessageBuilder)](rpc-binary.md) - Binary RPC API for high-performance Java-to-Java communication
- [JSON-RPC Reference](rpc-json.md) - Wire-format reference for the JSON-RPC API
- [RpcChain DSL](rpc-chain.md) - Java DSL for multi-step JSON-RPC workflows with automatic ObjectRef tracking
- [RPC Policy](rpc-policy.md) - Access control for RPC operations
- [Peers and Logs](peers-and-logs.md) - Understanding peers
- [Interception](interception.md) - Intercepting RPC calls
- [CLI Reference](../cli-reference.md) - Complete `pal peer call` and `pal log call` documentation
- [Distributed Application Guide](../guides/distributed-application.md) - Building with RPC
