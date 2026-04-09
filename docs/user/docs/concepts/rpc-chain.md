# RpcChain DSL

The `RpcChain` DSL is the highest of PAL's [three JSON-RPC abstraction layers](rpc.md#abstraction-layers) — a Java API for building multi-step JSON-RPC workflows where each step can depend on the results of previous ones. It handles object reference tracking automatically, so you can create objects, call methods on them, pass them as arguments to other calls, and read back results — all without manually managing ObjectRefs.

For single requests or lower-level control, see [JsonRpcMessageFactory](rpc.md#jsonrpcmessagefactory). For the raw wire format, see the [JSON-RPC API Reference](rpc.md#json-rpc-api-reference).

**Module**: `pal-client`

## The Problem

Calling instance methods over JSON-RPC requires managing ObjectRefs manually. To call a method on a remote object, you need to:

1. Send a constructor request
2. Parse the response to extract the ObjectRef
3. Include that ObjectRef in the next request

This makes multi-step workflows tedious and error-prone, especially when objects are passed as arguments to other objects. The RpcChain DSL eliminates this bookkeeping.

## Quick Example

```java
import io.quasient.pal.dsl.jsonrpc.RpcChain;
import io.quasient.pal.dsl.jsonrpc.RpcChainResult;
import static io.quasient.pal.dsl.jsonrpc.RpcChain.args;

RpcChain chain = new RpcChain(thinPeer);

chain
    .create("java.util.ArrayList", "myList")
    .call("add", args(42))
    .call("add", args(100))
    .call("size", "listSize")
    .send();

RpcChainResult result = chain.getChainResult();
result.getRef("myList");        // ObjectRef for the ArrayList
result.getValue("listSize");    // 2
```

Each `.create()` constructs an object on the remote peer and tracks its ObjectRef internally. Subsequent `.call()` invocations target that object. Named results (like `"listSize"`) can be retrieved after `.send()`.

## Connecting to a Peer

Before using RpcChain, you need a `ThinPeer` connected to a running peer via JSON-RPC.

### Lookup by UUID

```java
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.messages.types.RpcType;

DirectoryConnectionProvider directoryProvider =
    new DirectoryConnectionProvider("localhost:2379");

PeerInfo peer = directoryProvider.get()
    .orElseThrow()
    .getPeer(targetPeerUuid);

ThinPeer thinPeer = new ThinPeer()
    .withUuid(UUID.randomUUID())
    .withDirectoryProvider(directoryProvider)
    .withInitialPeer(peer)
    .withOutboundRpcType(RpcType.JSON_RPC)
    .init();
```

### Lookup by name

Peers registered with a name can be looked up by that name (names must be unique):

```java
PeerInfo peer = directoryProvider.get()
    .orElseThrow()
    .getPeerByName("my-service");
```

### Direct connection by address

If you know the peer's WebSocket address, connect directly without a directory:

```java
PeerInfo peer = new PeerInfo();
peer.setJsonrpcAddress("ws://192.168.1.100:9001");

ThinPeer thinPeer = new ThinPeer()
    .withUuid(UUID.randomUUID())
    .withInitialPeer(peer)
    .withOutboundRpcType(RpcType.JSON_RPC)
    .init();
```

### Using with RpcChain

```java
// Use it with RpcChain
RpcChain chain = new RpcChain(thinPeer);

// ... use the chain ...

// Clean up when done
thinPeer.close();
```

## Operations

### Creating Objects

```java
// Create with no arguments
chain.create("com.example.User", "user");

// Create with constructor arguments
chain.create("com.example.User", "user", args("Alice", 30));

// Create without naming (result accessible by index, not name)
chain.create("com.example.User");
```

After `.create()`, the chain context switches to the new instance — subsequent `.call()`, `.get()`, and `.put()` target it.

### Instance Method Calls

```java
chain
    .create("java.util.ArrayList", "list")
    .call("add", args(42))              // call with args, no result stored
    .call("size", "n")                  // call storing result as "n"
    .call("get", "first", args(0))      // call with args, storing result
    .send();

chain.getChainResult().getValue("n");       // 1
chain.getChainResult().getValue("first");   // 42
```

### Static Method Calls

```java
chain
    .callStatic("java.lang.Integer", "parseInt", "num", args("42"))
    .send();

chain.getChainResult().getValue("num"); // 42
```

### Field Access

```java
// Instance fields
chain
    .create("com.example.Config", "cfg")
    .get("timeout", "t")           // read field
    .put("timeout", 5000)          // write field
    .send();

// Static fields
chain
    .getStatic("java.lang.Integer", "MAX_VALUE", "maxInt")
    .putStatic("com.example.Config", "DEBUG", true)
    .send();
```

### Working with Multiple Objects

Use `.with()` to switch the chain context back to a previously created instance:

```java
chain
    .create("java.util.ArrayList", "listOne")
    .call("add", args(11))
    .call("add", args(22))
    .create("java.util.ArrayList", "listTwo")
    .call("add", args(33))
    .with("listOne")                           // switch back to listOne
    .call("addAll", "changed", args("listTwo"))  // pass listTwo by name
    .call("size", "totalSize")
    .send();

chain.getChainResult().getValue("totalSize");  // 3
chain.getChainResult().getValue("changed");    // true
```

When a named variable (like `"listTwo"`) is passed as an argument, the DSL automatically resolves it to the corresponding ObjectRef.

### Passing Objects as Arguments

Named instances can be passed as arguments to constructors, methods, or field setters. The DSL resolves the name to the ObjectRef at send time:

```java
chain
    .create("java.util.HashSet", "mySet")
    .call("add", args(1))
    .call("add", args(2))
    // Pass "mySet" as constructor argument — resolved to its ObjectRef
    .create("java.util.ArrayList", "myList", args("mySet"))
    .call("size", "listSize")
    .send();

chain.getChainResult().getValue("listSize");  // 2
```

### Nested Calls

Objects created inline can be passed directly as arguments:

```java
chain
    .create("java.util.HashMap", "map")
    .call("put", args(
        "key",
        chain
            .create("java.util.ArrayList", "innerList")
            .call("add", args(10))
            .call("add", args(20))
    ))
    .call("size", "mapSize")
    .with("innerList")
    .call("size", "listSize")
    .send();

chain.getChainResult().getValue("mapSize");   // 1
chain.getChainResult().getValue("listSize");  // 2
```

## Thread Affinity

You can hint which thread should execute a specific operation on the remote peer:

```java
chain
    .create("com.example.Widget", "w")
    .onFxThread()                              // next op runs on "fx-thread"
    .call("render")
    .withThreadAffinity("worker-pool")         // custom affinity
    .call("computeLayout", "layout")
    .send();
```

The affinity is consumed after one operation and resets to `null`.

## Reading Results

After `.send()`, retrieve results via `RpcChainResult`:

```java
RpcChainResult result = chain.getChainResult();

// By variable name
Object value = result.getValue("myVar");
ObjectRef ref = result.getRef("myInstance");

// All named variables
List<String> names = result.getAllVarNames();

// All responses (including unnamed)
List<Map<String, Object>> all = result.getAllValues();
// Each map has: "requestId", "varName", "value", "ref", "error"
```

## Error Handling

If any step in the chain returns a JSON-RPC error, `send()` throws a `RuntimeException` immediately. The chain does not continue past the failed step.

```java
try {
    chain
        .create("com.example.Calculator", "calc")
        .call("divide", "result", args(10, 0))
        .send();
} catch (RuntimeException e) {
    // "Error returned in response: ..."
}
```

## Further Reading

- [Remote Procedure Calls](rpc.md) - RPC overview, formats, and CLI usage
- [Object References](rpc.md#object-references) - How PAL manages remote object lifecycle
- [Peers and Logs](peers-and-logs.md) - Understanding peers and ThinPeer
