# JSON-RPC Reference

This page is the complete reference for PAL's JSON-RPC 2.0 API: wire-format messages, supported methods, argument types, error codes, validation rules, and the `JsonRpcMessageFactory` Java helper for building requests programmatically.

For an overview of how RPC fits into PAL, see [Remote Procedure Calls](rpc.md). For the binary RPC counterpart (Colfer over ZeroMQ), see [Binary RPC (MessageBuilder)](rpc-binary.md). For multi-step workflows with automatic ObjectRef tracking, see the [RpcChain DSL](rpc-chain.md).

## Message Structure

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

### Result envelope

A non-error `result` always carries either `void: true` or `void: false` plus a `value` object:

- **`void: true`** — the method returned `void`; no `value` is present.
- **`void: false`** — the method returned a value, described by `value`:
    - **`value.type`** — the Java type of the return value (always present).
    - **`value.null: true`** — the return value is `null` (no `value.value` or `value.ref` follows).
    - **`value.ref: N`** — the return value is a non-primitive object; `N` is its integer ObjectRef ID, usable in subsequent calls.
    - **`value.value: "..."`** — the return value is a primitive, wrapper, or string. **Scalars are rendered as JSON strings** (e.g. `"value": "8"` for an `int` result of `8`), since JSON does not natively distinguish `int`, `long`, `double`, etc.; use the `type` field to coerce the literal on the client side.

Concrete examples for each shape appear in the [Methods](#methods) section below.

## Methods

PAL recognises six top-level methods: `new`, `call`, `get`, `put`, `control`, and `meta`.

### `new` — Constructor

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

### `call` — Method Invocation

#### Static method

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

#### Instance method

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

#### Void method

When the method returns void, the response has `"void": true`:

```json
{
  "jsonrpc": "2.0", "id": "4",
  "result": {
    "void": true
  }
}
```

#### Returning an ObjectRef

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

#### Returning null

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

Note the asymmetry between *passing* `null` as an argument and *receiving* `null` as a return: arguments use `{"type": "...", "value": null}` (the JSON literal `null` under the `value` key), while responses use `{"type": "...", "null": true}` (a boolean key).

### `get` — Field Read

#### Static field

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

#### Instance field

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

### `put` — Field Write

#### Static field

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

#### Instance field

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

### `control` — Session Management

Control messages manage object lifecycle on the peer and provide basic peer-level commands. They use `method: "control"` at the top level, with a sub-method in `params`.

#### Delete an object

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

#### Delete a session

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

#### Trigger garbage collection

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

#### Ping

Liveness check. Accepts no arguments and returns void. Useful for verifying that a peer's RPC interface is reachable without performing any side effects.

```json
{
  "jsonrpc": "2.0", "id": "15",
  "method": "control",
  "params": {
    "method": "ping"
  }
}
```

`JsonRpcMessageFactory` does not currently expose a builder for `ping`; build it from raw JSON if you need it from Java.

All control messages return a void result.

### `meta` — Metadata Query

Queries metadata about RPC-accessible classes, methods, and fields on the peer. The `meta` method is a placeholder for future metadata services; currently it supports a single service: `fetch_classes_info`.

#### Fetch classes info

Scans the peer's classpath and returns metadata for the classes, constructors, methods, and fields the caller is permitted to invoke via RPC. The output is filtered through the peer's currently active [RPC policy](rpc-policy.md), so what you see is exactly what you can call — anything the policy denies is omitted. Policy hot-reloads are visible on the next scan, so re-issuing the request after a policy change reflects the new permissions without restarting the peer.

Use this to discover the callable surface of a remote peer without prior knowledge of its codebase, or to verify what a given policy actually exposes.

```json
{
  "jsonrpc": "2.0", "id": "16",
  "method": "meta",
  "params": {
    "method": "fetch_classes_info",
    "args": [
      {"name": "compress_encode",  "type": "boolean",            "value": true},
      {"name": "merge_ancestry",   "type": "boolean",            "value": false},
      {"name": "exclude_prefixes", "type": "java.lang.String[]", "value": ["java.lang.", "sun.misc."]},
      {"name": "include_classes",  "type": "java.lang.String[]", "value": ["com.example.MyClass"]}
    ]
  }
}
```

| Argument | Type | Default | Description |
|----------|------|---------|-------------|
| `compress_encode` | boolean | `true` | Compress and base64-encode the response body. Class metadata for a real classpath can be very large, so compression is on by default; set to `false` only when you want raw JSON for inspection. |
| `merge_ancestry` | boolean | `false` | Include inherited methods and fields from superclasses and interfaces. |
| `exclude_prefixes` | string[] | _(none)_ | Class-name prefixes to exclude (e.g., `java.lang.`, `sun.misc.`). |
| `include_classes` | string[] | _(all)_ | When set, only the listed fully-qualified class names are included. |

The response carries a status code (`OK`, `ERROR`, `UNAUTHORIZED`, `UNSUPPORTED`) and a body containing either the encoded class metadata or an error message.

In Java, prefer [`JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage`](#meta-and-control-messages) over hand-built JSON for `meta` requests.

## Argument Types

Arguments appear in the `args` array and in the `value` field of `put` requests. Each argument is an object with `type` and `value`, or an object reference with `ref`.

### Primitives and wrappers

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

### Strings

```json
{"type": "java.lang.String", "value": "hello"}
{"type": "String", "value": "hello"}
```

### Null values

Specify the type but set the value to `null`:

```json
{"type": "java.lang.Integer", "value": null}
{"type": "java.lang.String",  "value": null}
```

### Object references

Pass a previously obtained ObjectRef by its ID:

```json
{"ref": 42}
```

This allows chaining: create an object with `new`, then pass it as an argument to a `call`.

### Arrays

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

#### Numeric suffixes

The outer `type` (e.g. `"double[]"`) tells PAL the array's component type, but JSON has no native distinction between `int`, `long`, `double`, and `float` — every JSON number arrives as the same generic numeric token. Append a suffix to each *inner* literal to disambiguate:

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

### Collections

#### ArrayList

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

#### HashMap

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

## Params Reference

Summary of all `params` fields:

| Field | Type | Used by | Description |
|-------|------|---------|-------------|
| `type` | string | `new`, `call`, `get`, `put` | Fully-qualified class name |
| `method` | string | `call`, `control`, `meta` | Method name (or sub-method name for `control` / `meta`) |
| `field` | string | `get`, `put` | Field name to read or write |
| `instance` | integer | `call`, `get`, `put` | ObjectRef ID for instance operations (omit for static) |
| `args` | array | `new`, `call`, `control`, `meta` | Typed argument array |
| `value` | object | `put` | Typed argument for the new field value |

Required fields per method:

| Method | Required | Optional |
|--------|----------|----------|
| `new` | `type` | `args` |
| `call` | `type`, `method` | `instance`, `args` |
| `get` | `type`, `field` | `instance` |
| `put` | `type`, `field`, `value` | `instance` |
| `control` | `method` | `args` |
| `meta` | `method` | `args` |

## Error Response Format

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

### Error codes

| Code | Name | When |
|------|------|------|
| -32700 | Parse error | Malformed JSON (syntax error) |
| -32600 | Invalid Request | Missing or unrecognised `method` field |
| -32602 | Invalid params | Missing required params (`type`, `field`, `value`), invalid characters in type name, Java reserved keyword used as type |
| -32601 | Method not found | `ClassNotFoundException`, `NoSuchMethodException`, `NoSuchFieldException` |
| -32603 | Internal error | Unexpected server-side failure |
| -32000 | Server error | Generic server error |
| -32001 | RPC access denied | Operation blocked by [RPC policy](rpc-policy.md) |

### Common error scenarios

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

## Input Validation

The peer validates requests before dispatching. The following are rejected with `-32602 Invalid params`:

- Missing `params` object entirely
- Missing `type` in `params`
- `type` containing invalid characters (e.g., `:`, `/`)
- `type` that is a Java reserved keyword (e.g., `try`, `class`)
- `call` without `method` in params
- `get` without `field` in params
- `put` without `field` or `value` in params

Invalid or unrecognised top-level `method` values (anything other than `new`, `call`, `get`, `put`, `control`, `meta`) are rejected with `-32600 Invalid Request`.

## Member Visibility

PAL's JSON-RPC dispatch can access members of **any** Java visibility level — public, protected, package-private, and private. This is powerful for testing and debugging, but should be restricted in production.

Use the [RPC policy system](rpc-policy.md) to control which visibility levels are accessible. The `deny-nonpublic` preset restricts RPC to public members only:

```bash
pal run -d localhost:2379 --json-rpc auto \
  --rpc-policy-preset deny-nonpublic,deny-unsafe \
  -cp app.jar com.example.Main
```

See [RPC Policy — Visibility Filtering](rpc-policy.md#visibility-filtering) for fine-grained control.

## Typical Session

A complete interaction often follows this pattern:

```
Client                              Peer
  │                                   │
  │── new Calculator ────────────────▶│
  │◀──────────────── ref:42 ──────────│
  │                                   │
  │── call add(5,3) on ref:42 ───────▶│
  │◀──────────────── value:8 ─────────│
  │                                   │
  │── get "result" on ref:42 ────────▶│
  │◀──────────────── value:8 ─────────│
  │                                   │
  │── control delete_object ref:42 ──▶│
  │◀──────────────── void ────────────│
```

For multi-step workflows in Java code, the [RpcChain DSL](rpc-chain.md) handles ObjectRef management automatically.

## JsonRpcMessageFactory

**Module**: `pal-api` — `io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory`

The factory is a static utility class that builds `JsonRpcRequest` objects programmatically. It sits between raw JSON and the RpcChain DSL: you get type-safe request construction and automatic ID generation, but you still send each request individually and manage ObjectRefs yourself.

### Building arguments

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

### Constructor call

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

### Static method call

```java
JsonRpcRequest req = JsonRpcMessageFactory.buildClassMethodCall(
    "com.example.Calculator", "add",
    List.of(
        Argument.builder().withValue(5).withType("int").build(),
        Argument.builder().withValue(3).withType("int").build()
    ));
```

### Instance method call

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

### Field access

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

### Meta and control messages

```java
// Fetch classes info (metadata query)
JsonRpcRequest req = JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(
    new String[]{"com.example.MyClass"},          // includeClasses (or null)
    new String[]{"java.lang.", "sun.misc."},      // excludePrefixes (or null)
    true,                                          // compressAndEncode
    false);                                        // mergeAncestry

// Delete a single object from the session
JsonRpcRequest req = JsonRpcMessageFactory.buildDeleteObjectCommandMessage(objectRef);

// Delete the entire session
JsonRpcRequest req = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();

// Trigger garbage collection
JsonRpcRequest req = JsonRpcMessageFactory.buildGcCommandMessage();
```

The factory does not currently expose a builder for the `ping` control sub-method; build it from raw JSON if needed.

### Sending requests

The factory builds requests; sending them is done via `ThinPeer`. For ThinPeer setup (lookup by UUID/name, direct address, configuring with logs), see [Binary RPC → Connecting to a Peer](rpc-binary.md#connecting-to-a-peer) — the same setup applies, except `withOutboundRpcType(RpcType.JSON_RPC)` is used in place of `RpcType.ZMQ_RPC`.

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

### When to use the factory vs RpcChain

Use the factory when you need to:

- Send a single request (no chaining needed)
- Control request timing or ordering yourself
- Interleave RPC calls with other logic between each step
- Work at a lower level than the DSL allows

Use the [RpcChain DSL](rpc-chain.md) when you have multi-step workflows with object dependencies — it eliminates the ObjectRef tracking boilerplate that the factory requires you to do manually.

## Further Reading

- [Remote Procedure Calls](rpc.md) — Concept overview and RPC system architecture
- [Binary RPC (MessageBuilder)](rpc-binary.md) — Binary RPC API for high-performance Java-to-Java communication
- [RpcChain DSL](rpc-chain.md) — Java DSL for multi-step JSON-RPC workflows with automatic ObjectRef tracking
- [RPC Policy](rpc-policy.md) — Access control for RPC operations
- [CLI Reference](../cli-reference.md) — Complete `pal peer call` and `pal log call` documentation
