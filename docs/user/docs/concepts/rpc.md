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

## JSON-RPC Abstraction Layers

PAL's JSON-RPC API is available at three levels of abstraction. Pick the one that matches your use case:

| Level | What you use | Best for |
|-------|-------------|----------|
| **Raw JSON** | Hand-written JSON-RPC 2.0 messages | Cross-language clients, shell scripts, debugging |
| **JsonRpcMessageFactory** | Java factory that builds typed `JsonRpcRequest` objects | Java clients that need full control over individual requests |
| **RpcChain DSL** | Fluent Java API that chains operations and tracks ObjectRefs | Multi-step workflows where objects are created, passed around, and queried |

Each layer produces the same wire-format messages — they differ only in how much bookkeeping the caller does:

- **Raw JSON** — you construct the JSON, manage request IDs, and track ObjectRefs yourself. See [JSON-RPC API Reference](#json-rpc-api-reference) below.
- **JsonRpcMessageFactory** — you call Java methods that return `JsonRpcRequest` objects; the factory handles JSON structure and ID generation. See [JsonRpcMessageFactory](#jsonrpcmessagefactory) in the API Reference.
- **RpcChain DSL** — you describe a sequence of operations; the DSL handles ObjectRef resolution, request ordering, and result extraction. See [RpcChain DSL](rpc-chain.md).

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

## JSON-RPC API Reference

This section is the complete reference for PAL's JSON-RPC 2.0 API. All examples below show raw JSON messages as they appear on the WebSocket wire.

### Message Structure

Every request follows the JSON-RPC 2.0 envelope:

```json
{
  "jsonrpc": "2.0",
  "id": "req-1",
  "method": "<method>",
  "params": { ... }
}
```

Every response is either a **result** or an **error** (never both):

```json
{
  "jsonrpc": "2.0",
  "id": "req-1",
  "result": { ... }
}
```

```json
{
  "jsonrpc": "2.0",
  "id": "req-1",
  "error": { ... }
}
```

### Methods

PAL recognises five top-level methods: `new`, `call`, `get`, `put`, and `control`.

#### `new` — Constructor

Creates a new object on the peer. Returns an ObjectRef that can be used in subsequent calls.

```json
{
  "jsonrpc": "2.0", "id": "1",
  "method": "new",
  "params": {
    "type": "com.example.Calculator"
  }
}
```

With constructor arguments:

```json
{
  "jsonrpc": "2.0", "id": "2",
  "method": "new",
  "params": {
    "type": "com.example.User",
    "args": [
      {"type": "java.lang.String", "value": "john"},
      {"type": "int", "value": 30}
    ]
  }
}
```

**Response** — the `ref` field is the ObjectRef ID:

```json
{
  "jsonrpc": "2.0", "id": "2",
  "result": {
    "void": false,
    "value": {
      "type": "com.example.User",
      "null": false,
      "ref": 42
    }
  }
}
```

#### `call` — Method Invocation

##### Static method

Omit `instance` to call a static method:

```json
{
  "jsonrpc": "2.0", "id": "3",
  "method": "call",
  "params": {
    "type": "com.example.Calculator",
    "method": "add",
    "args": [
      {"type": "int", "value": 5},
      {"type": "int", "value": 3}
    ]
  }
}
```

**Response** — value result:

```json
{
  "jsonrpc": "2.0", "id": "3",
  "result": {
    "void": false,
    "value": {
      "type": "int",
      "null": false,
      "value": "8"
    }
  }
}
```

##### Instance method

Include `instance` with the ObjectRef ID from a prior `new` or `call`:

```json
{
  "jsonrpc": "2.0", "id": "4",
  "method": "call",
  "params": {
    "type": "java.util.ArrayList",
    "method": "add",
    "instance": 42,
    "args": [
      {"type": "java.lang.String", "value": "hello"}
    ]
  }
}
```

##### Void method

When the method returns void, the response has `"void": true`:

```json
{
  "jsonrpc": "2.0", "id": "4",
  "result": {
    "void": true
  }
}
```

##### Returning an ObjectRef

When a method returns a non-primitive object, the response includes a `ref`:

```json
{
  "jsonrpc": "2.0", "id": "5",
  "result": {
    "void": false,
    "value": {
      "type": "com.example.Calculator",
      "null": false,
      "ref": 99
    }
  }
}
```

##### Returning null

```json
{
  "jsonrpc": "2.0", "id": "6",
  "result": {
    "void": false,
    "value": {
      "type": "java.lang.String",
      "null": true
    }
  }
}
```

#### `get` — Field Read

##### Static field

```json
{
  "jsonrpc": "2.0", "id": "7",
  "method": "get",
  "params": {
    "type": "com.example.Config",
    "field": "VERSION"
  }
}
```

##### Instance field

Include `instance` to read from a specific object:

```json
{
  "jsonrpc": "2.0", "id": "8",
  "method": "get",
  "params": {
    "type": "com.example.User",
    "field": "name",
    "instance": 42
  }
}
```

#### `put` — Field Write

##### Static field

The `value` field is a typed argument (same format as `args` entries):

```json
{
  "jsonrpc": "2.0", "id": "9",
  "method": "put",
  "params": {
    "type": "com.example.Config",
    "field": "debugMode",
    "value": {"type": "boolean", "value": true}
  }
}
```

##### Instance field

```json
{
  "jsonrpc": "2.0", "id": "10",
  "method": "put",
  "params": {
    "type": "com.example.User",
    "field": "name",
    "instance": 42,
    "value": {"type": "java.lang.String", "value": "jane"}
  }
}
```

Setting a field to null:

```json
{
  "jsonrpc": "2.0", "id": "11",
  "method": "put",
  "params": {
    "type": "com.example.User",
    "field": "nickname",
    "instance": 42,
    "value": {"type": "java.lang.String", "value": null}
  }
}
```

`put` always returns a void result.

#### `control` — Session Management

Control messages manage object lifecycle on the peer. They use `method: "control"` at the top level, with a sub-method in `params`.

##### Delete an object

Removes a single object from the peer's session. Subsequent operations on this ObjectRef will fail with `NullPointerException`.

```json
{
  "jsonrpc": "2.0", "id": "12",
  "method": "control",
  "params": {
    "method": "delete_object",
    "args": [{"ref": 42}]
  }
}
```

##### Delete a session

Removes **all** objects from the caller's session on the peer.

```json
{
  "jsonrpc": "2.0", "id": "13",
  "method": "control",
  "params": {
    "method": "delete_session"
  }
}
```

##### Trigger garbage collection

Requests the peer to run `System.gc()`.

```json
{
  "jsonrpc": "2.0", "id": "14",
  "method": "control",
  "params": {
    "method": "gc"
  }
}
```

All control messages return a void result.

### Argument Types

Arguments appear in the `args` array and in the `value` field of `put` requests. Each argument is an object with `type` and `value`, or an object reference with `ref`.

#### Primitives and wrappers

```json
{"type": "int",     "value": 42}
{"type": "long",    "value": 9223372036854775807}
{"type": "double",  "value": 3.14}
{"type": "float",   "value": 1.5}
{"type": "boolean", "value": true}
{"type": "byte",    "value": 127}
{"type": "short",   "value": 32767}
{"type": "char",    "value": "A"}
```

Wrapper types use the fully-qualified name:

```json
{"type": "java.lang.Integer", "value": 42}
{"type": "java.lang.Double",  "value": 3.14}
```

#### Strings

```json
{"type": "java.lang.String", "value": "hello"}
{"type": "String", "value": "hello"}
```

#### Null values

Specify the type but set the value to `null`:

```json
{"type": "java.lang.Integer", "value": null}
{"type": "java.lang.String",  "value": null}
```

#### Object references

Pass a previously obtained ObjectRef by its ID:

```json
{"ref": 42}
```

This allows chaining: create an object with `new`, then pass it as an argument to a `call`.

#### Arrays

Arrays use `type` to specify the array type and `value` as a JSON array:

```json
{"type": "int[]",    "value": [1, 2, 3]}
{"type": "double[]", "value": [1.5, 2.5, 3.5]}
{"type": "String[]", "value": ["a", "b", "c"]}
```

Null and empty arrays:

```json
{"type": "int[]", "value": null}
{"type": "int[]", "value": []}
```

##### Numeric suffixes

When using inline numeric values in arrays (without explicit `type` wrappers), append a suffix to disambiguate the numeric type:

| Suffix | Type |
|--------|------|
| `d` | double (`3.14d`) |
| `f` | float (`1.5f`) |
| `l` | long (`100l`) |

Integer values need no suffix. Example:

```json
{"type": "double[]", "value": [239823d, 38723d, 2323d]}
{"type": "float[]",  "value": [23f, 1f, 3f]}
{"type": "long[]",   "value": [2398239l, -23l]}
```

#### Collections

##### ArrayList

```json
{
  "type": "java.util.ArrayList",
  "value": [
    {"type": "java.lang.Integer", "value": 39},
    {"type": "java.lang.Integer", "value": 5},
    {"type": "java.lang.Integer", "value": 58}
  ]
}
```

##### HashMap

```json
{
  "type": "java.util.HashMap",
  "value": {
    "key1": 39.0,
    "key2": 5.8,
    "key3": 42.98
  }
}
```

### Params Reference

Summary of all `params` fields:

| Field | Type | Used by | Description |
|-------|------|---------|-------------|
| `type` | string | `new`, `call`, `get`, `put` | Fully-qualified class name |
| `method` | string | `call`, `control` | Method name to invoke |
| `field` | string | `get`, `put` | Field name to read or write |
| `instance` | integer | `call`, `get`, `put` | ObjectRef ID for instance operations (omit for static) |
| `args` | array | `new`, `call`, `control` | Typed argument array |
| `value` | object | `put` | Typed argument for the new field value |

Required fields per method:

| Method | Required | Optional |
|--------|----------|----------|
| `new` | `type` | `args` |
| `call` | `type`, `method` | `instance`, `args` |
| `get` | `type`, `field` | `instance` |
| `put` | `type`, `field`, `value` | `instance` |
| `control` | `method` | `args` |

### Error Response Format

When an operation fails, the response contains an `error` object with a standard JSON-RPC 2.0 error code, a human-readable message, and a `data` object with details:

```json
{
  "jsonrpc": "2.0", "id": "1",
  "error": {
    "code": -32601,
    "message": "Method not found",
    "data": {
      "throwable_type": "java.lang.ClassNotFoundException",
      "message": "com.example.DoesNotExist",
      "request_id": "1",
      "stack_trace": ["..."],
      "cause": null
    }
  }
}
```

#### Error codes

| Code | Name | When |
|------|------|------|
| -32700 | Parse error | Malformed JSON (syntax error) |
| -32600 | Invalid Request | Missing or unrecognised `method` field |
| -32602 | Invalid params | Missing required params (`type`, `field`, `value`), invalid characters in type name, Java reserved keyword used as type |
| -32601 | Method not found | `ClassNotFoundException`, `NoSuchMethodException`, `NoSuchFieldException` |
| -32603 | Internal error | Unexpected server-side failure |
| -32000 | Server error | Generic server error |
| -32001 | RPC access denied | Operation blocked by [RPC policy](rpc-policy.md) |

#### Common error scenarios

**Class not found:**

```json
{"code": -32601, "message": "Method not found",
 "data": {"throwable_type": "java.lang.ClassNotFoundException",
          "message": "com.example.DoesNotExist"}}
```

**No matching method/constructor:**

```json
{"code": -32601, "message": "Method not found",
 "data": {"throwable_type": "java.lang.NoSuchMethodException",
          "message": "No matching constructor found"}}
```

**Runtime exception in remote method:**

```json
{"code": -32000, "message": "Server error",
 "data": {"throwable_type": "java.lang.ArithmeticException",
          "message": "/ by zero",
          "stack_trace": ["..."]}}
```

**Missing required field:**

```json
{"code": -32602, "message": "Invalid params",
 "data": {"message": "Field is missing in 'get' request"}}
```

### Input Validation

The peer validates requests before dispatching. The following are rejected with `-32602 Invalid params`:

- Missing `params` object entirely
- Missing `type` in `params`
- `type` containing invalid characters (e.g., `:`, `/`)
- `type` that is a Java reserved keyword (e.g., `try`, `class`)
- `call` without `method` in params
- `get` without `field` in params
- `put` without `field` or `value` in params

Invalid or unrecognised top-level `method` values (anything other than `new`, `call`, `get`, `put`, `control`) are rejected with `-32600 Invalid Request`.

### Member Visibility

PAL's JSON-RPC dispatch can access members of **any** Java visibility level — public, protected, package-private, and private. This is powerful for testing and debugging, but should be restricted in production.

Use the [RPC policy system](rpc-policy.md) to control which visibility levels are accessible. The `deny-nonpublic` preset restricts RPC to public members only:

```bash
pal run -d localhost:2379 --json-rpc auto \
  --rpc-policy-preset deny-nonpublic,deny-unsafe \
  -cp app.jar com.example.Main
```

See [RPC Policy — Visibility Filtering](rpc-policy.md#visibility-filtering) for fine-grained control.

### Typical Session

A complete interaction often follows this pattern:

```
Client                              Peer
  │                                   │
  │── new Calculator ────────────────▶│
  │◀──────────────── ref:42 ──────────│
  │                                   │
  │── call add(5,3) on ref:42 ──────▶│
  │◀──────────────── value:8 ─────────│
  │                                   │
  │── get "result" on ref:42 ───────▶│
  │◀──────────────── value:8 ─────────│
  │                                   │
  │── control delete_object ref:42 ─▶│
  │◀──────────────── void ────────────│
```

For multi-step workflows in Java code, the [RpcChain DSL](rpc-chain.md) handles ObjectRef management automatically.

### JsonRpcMessageFactory

**Module**: `pal-api` — `io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory`

The factory is a static utility class that builds `JsonRpcRequest` objects programmatically. It sits between raw JSON and the RpcChain DSL: you get type-safe request construction and automatic ID generation, but you still send each request individually and manage ObjectRefs yourself.

#### Building arguments

Arguments are constructed with `Argument.builder()`:

```java
import io.quasient.pal.messages.jsonrpc.Argument;

// Typed value
Argument intArg = Argument.builder().withValue(42).withType("int").build();
Argument strArg = Argument.builder().withValue("hello").withType("java.lang.String").build();

// Null value (type required)
Argument nullArg = Argument.builder().withValue(null).withType("java.lang.Integer").build();

// Object reference (from a prior response)
Argument refArg = Argument.builder().withRef(objectRef).build();

// Collection
ArrayList<Integer> list = new ArrayList<>(List.of(1, 2, 3));
Argument listArg = Argument.builder().withValue(list).withType("java.util.ArrayList").build();
```

#### Constructor call

```java
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;

// No-arg constructor
JsonRpcRequest req = JsonRpcMessageFactory.buildConstructorCall(
    "com.example.Calculator", List.of());

// With arguments
JsonRpcRequest req = JsonRpcMessageFactory.buildConstructorCall(
    "com.example.User",
    List.of(
        Argument.builder().withValue("Alice").withType("java.lang.String").build(),
        Argument.builder().withValue(30).withType("int").build()
    ));
```

#### Static method call

```java
JsonRpcRequest req = JsonRpcMessageFactory.buildClassMethodCall(
    "com.example.Calculator", "add",
    List.of(
        Argument.builder().withValue(5).withType("int").build(),
        Argument.builder().withValue(3).withType("int").build()
    ));
```

#### Instance method call

Pass an `ObjectRef` (from a constructor or method response) or a raw integer ID:

```java
// With ObjectRef
JsonRpcRequest req = JsonRpcMessageFactory.buildInstanceMethodCall(
    "java.util.ArrayList", "add", objectRef,
    List.of(Argument.builder().withValue("item").withType("java.lang.String").build()));

// With integer ID
JsonRpcRequest req = JsonRpcMessageFactory.buildInstanceMethodCall(
    "java.util.ArrayList", "size", 42, List.of());
```

#### Field access

```java
// Static get / put
JsonRpcRequest get = JsonRpcMessageFactory.buildStaticFieldGet(
    "com.example.Config", "VERSION");

JsonRpcRequest put = JsonRpcMessageFactory.buildStaticFieldPut(
    "com.example.Config", "debugMode",
    Argument.builder().withValue(true).withType("boolean").build());

// Instance get / put
JsonRpcRequest get = JsonRpcMessageFactory.buildInstanceFieldGet(
    "com.example.User", objectRef, "name");

JsonRpcRequest put = JsonRpcMessageFactory.buildInstanceFieldPut(
    "com.example.User", objectRef, "name",
    Argument.builder().withValue("Bob").withType("java.lang.String").build());
```

#### Control messages

```java
// Delete a single object from the session
JsonRpcRequest req = JsonRpcMessageFactory.buildDeleteObjectCommandMessage(objectRef);

// Delete the entire session
JsonRpcRequest req = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();

// Trigger garbage collection
JsonRpcRequest req = JsonRpcMessageFactory.buildGcCommandMessage();
```

#### Sending requests

The factory builds requests; sending them is done via `ThinPeer`:

```java
// Send to peer and wait for response
JsonRpcResponse response = thinPeer.sendJsonRpcRequestToPeer(request).get();

// Check result
if (response.getError() != null) {
    // Handle error
    System.err.println(response.getError().getData().getMessage());
} else if (response.getResult().getIsVoid()) {
    // Void return
} else {
    // Extract value or ref
    ResponseObject value = response.getResult().getValue();
    Integer ref = value.getRef();       // ObjectRef ID (if object)
    String type = value.getType();      // Return type
}
```

#### When to use the factory vs RpcChain

Use the factory when you need to:

- Send a single request (no chaining needed)
- Control request timing or ordering yourself
- Interleave RPC calls with other logic between each step
- Work at a lower level than the DSL allows

Use the [RpcChain DSL](rpc-chain.md) when you have multi-step workflows with object dependencies — it eliminates the ObjectRef tracking boilerplate that the factory requires you to do manually.

## Further Reading

- [RpcChain DSL](rpc-chain.md) - Java DSL for multi-step JSON-RPC workflows with automatic ObjectRef tracking
- [Peers and Logs](peers-and-logs.md) - Understanding peers
- [Interception](interception.md) - Intercepting RPC calls
- [CLI Reference](../cli-reference.md) - Complete `pal peer call` and `pal log call` documentation
- [Distributed Application Guide](../guides/distributed-application.md) - Building with RPC
