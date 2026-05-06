# Binary RPC (MessageBuilder)

PAL's binary RPC uses the [Colfer](https://github.com/pascaldekloe/colfer) serialization format over ZeroMQ sockets. It is the most compact and fastest wire format PAL supports — designed for high-throughput, low-latency communication between peers.

Unlike [JSON-RPC](rpc-json.md) where you can hand-craft messages, binary RPC is always used through Java APIs today. The `MessageBuilder` class is the primary entry point: it constructs `ExecMessage` and `ControlMessage` objects that a `ThinPeer` sends over ZeroMQ. The wire format itself is language-agnostic — Colfer has bindings in many languages and the `.colf` schemas are checked into the PAL repo — so a non-Java client is achievable; PAL just doesn't ship one out of the box.

**Module**: `pal-api` (MessageBuilder), `pal-client` (ThinPeer)

## When Binary RPC Is Used

Binary RPC appears in several contexts:

| Context | Description |
|---------|-------------|
| **Peer-to-peer RPC** | Direct method invocation between peers over ZeroMQ (`--zmq-rpc`) |
| **Intercept callbacks** | PAL dispatches intercept callbacks using binary messages |
| **Write-ahead log (WAL)** | Messages written to the WAL (Kafka or Chronicle) are in Colfer binary format |
| **PUB socket** | ZeroMQ PUB socket broadcasts use binary format |
| **CLI `pal peer call`** | The CLI uses binary RPC when connecting via `tcp://` addresses |

For debugging or human-readable messages, use [JSON-RPC](rpc-json.md) instead. Cross-language clients can also use binary RPC in principle — Colfer has bindings in many languages and PAL's `.colf` schemas are checked into the repo — but PAL currently ships only a Java client (`ThinPeer`), so JSON-RPC is the path of least resistance for non-Java callers today.

## Connecting to a Peer

Before using MessageBuilder, set up a `ThinPeer` connected to a running peer via ZeroMQ.

### Lookup by UUID

Pass the directory URL and a stub `PeerInfo` carrying just the target peer's UUID; ThinPeer resolves the UUID against the directory at `init()` time:

```java
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.messages.types.RpcType;

ThinPeer thinPeer = new ThinPeer()
    .withUuid(UUID.randomUUID())
    .withDirectoryUrl("localhost:2379")
    .withInitialPeer(new PeerInfo(targetPeerUuid))
    .withOutboundRpcType(RpcType.ZMQ_RPC)
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

If you know the peer's ZMQ address, connect directly without a directory:

```java
PeerInfo peer = new PeerInfo();
peer.setZmqRpcAddress("tcp://192.168.1.100:5555");

ThinPeer thinPeer = new ThinPeer()
    .withUuid(UUID.randomUUID())
    .withInitialPeer(peer)
    .withOutboundRpcType(RpcType.ZMQ_RPC)
    .init();
```

### Full example

```java
// Create MessageBuilder
MessageBuilder messageBuilder = new MessageBuilder();

// ... use messageBuilder + thinPeer ...

// Clean up when done
thinPeer.close();
```

If the peer also writes to a log (Kafka), configure the ThinPeer with Kafka properties and log names:

```java
ThinPeer thinPeer = new ThinPeer()
    .withUuid(clientId)
    .withDirectoryProvider(directoryProvider)
    .withConsumerProperties(kafkaConsumerProps)
    .withProducerProperties(kafkaProducerProps)
    .withOutputLog(sourceLog)
    .withInputLog(walLog)
    .withInitialPeer(peer)
    .withOutboundRpcType(RpcType.ZMQ_RPC)
    .init();
```

## MessageBuilder API

`MessageBuilder` constructs messages for all RPC operations. Every method returns either an `ExecMessage` (for operations that execute code) or a `ControlMessage` (for session management).

```java
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.common.objects.ObjectRef;
```

### Constructor Operations

Create objects on the remote peer.

#### No-argument constructor

```java
ExecMessage request = messageBuilder.buildEmptyConstructor(
    clientId,           // UUID - your peer's ID
    "com.example.User"  // fully qualified class name
);

ExecMessage response = thinPeer.sendToPeer(request);
ObjectRef userRef = ObjectRef.from(
    response.getReturnValue().getObject().getRef()
);
```

#### Constructor with arguments

Arguments are passed as parallel arrays: one for parameter type names, one for values, and one for ObjectRef arguments. For each parameter position, set the value in either `args` or `argObjRefs` (the other should be `null`).

```java
// Constructor: new User("Alice", 30)
ExecMessage request = messageBuilder.buildNonEmptyConstructor(
    clientId,
    "com.example.User",
    new String[]{"java.lang.String", "int"},  // parameter types
    new Object[]{"Alice", 30},                // argument values
    new ObjectRef[]{null, null}               // no ObjectRef args
);

ExecMessage response = thinPeer.sendToPeer(request);
ObjectRef userRef = ObjectRef.from(
    response.getReturnValue().getObject().getRef()
);
```

#### Constructor with an ObjectRef argument

When a constructor parameter is a remote object, pass its reference in `argObjRefs`:

```java
// First create a List
ExecMessage listRequest = messageBuilder.buildEmptyConstructor(
    clientId, "java.util.ArrayList"
);
ExecMessage listResponse = thinPeer.sendToPeer(listRequest);
ObjectRef listRef = ObjectRef.from(
    listResponse.getReturnValue().getObject().getRef()
);

// Pass the List reference to another constructor
ExecMessage request = messageBuilder.buildNonEmptyConstructor(
    clientId,
    "com.example.Container",
    new String[]{"java.util.List"},
    new Object[]{null},         // null in args — value comes from argObjRefs
    new ObjectRef[]{listRef}    // the ObjectRef for this parameter
);
```

### Method Calls

#### Static methods

```java
// Integer.parseInt("42")
ExecMessage request = messageBuilder.buildClassMethod(
    clientId,
    "java.lang.Integer",                  // class
    "parseInt",                           // method
    new String[]{"java.lang.String"},     // parameter types
    null,                                 // sender (not needed for external calls)
    null,                                 // sender ObjectRef
    new Object[]{"42"},                   // arguments
    new ObjectRef[]{null}                 // no ObjectRef args
);

ExecMessage response = thinPeer.sendToPeer(request);
```

For methods with no arguments, pass empty arrays:

```java
ExecMessage request = messageBuilder.buildClassMethod(
    clientId,
    "com.example.Service",
    "getInstance",
    new String[]{},
    null, null,
    new Object[]{},
    new ObjectRef[]{}
);
```

**Convenience overload** --- when no arguments are ObjectRefs, you can omit the `argObjRefs` array:

```java
ExecMessage request = messageBuilder.buildClassMethod(
    clientId,
    "java.lang.Integer",
    "parseInt",
    new String[]{"java.lang.String"},
    null, null,
    new Object[]{"42"}    // mixed args array (no separate ObjectRef array)
);
```

#### Instance methods

Instance methods require the `ObjectRef` of the target object:

```java
// list.add(42)
ExecMessage request = messageBuilder.buildInstanceMethod(
    clientId,
    "java.util.ArrayList",               // class
    "add",                                // method
    listRef,                              // target object
    new String[]{"java.lang.Object"},     // parameter types
    new Object[]{42},                     // arguments
    new ObjectRef[]{null}                 // no ObjectRef args
);

ExecMessage response = thinPeer.sendToPeer(request);
```

#### Passing ObjectRefs as method arguments

```java
// list.addAll(otherList)   — otherList is a remote object
ExecMessage request = messageBuilder.buildInstanceMethod(
    clientId,
    "java.util.ArrayList",
    "addAll",
    listRef,                                  // target
    new String[]{"java.util.Collection"},     // parameter types
    new Object[]{null},                       // null — value is in argObjRefs
    new ObjectRef[]{otherListRef}             // the ObjectRef argument
);
```

### Field Access

#### Read a static field

```java
// Integer.MAX_VALUE
ExecMessage request = messageBuilder.buildGetStatic(
    clientId,
    "java.lang.Integer",
    "MAX_VALUE"
);

ExecMessage response = thinPeer.sendToPeer(request);
```

#### Read an instance field

```java
// user.name
ExecMessage request = messageBuilder.buildGetObject(
    clientId,
    "com.example.User",
    "name",
    userRef    // ObjectRef of the target instance
);

ExecMessage response = thinPeer.sendToPeer(request);
```

#### Write a static field

```java
// Config.DEBUG = true
ExecMessage request = messageBuilder.buildPutStatic(
    clientId,
    "com.example.Config",
    "DEBUG",
    "boolean",    // value's type name
    true          // new value
);

ExecMessage response = thinPeer.sendToPeer(request);
```

You can also set a static field to a remote object:

```java
ExecMessage request = messageBuilder.buildPutStatic(
    clientId,
    "com.example.Config",
    "instance",
    configRef     // ObjectRef to set
);
```

#### Write an instance field

```java
// user.name = "Bob"
ExecMessage request = messageBuilder.buildPutObject(
    clientId,
    "com.example.User",
    "name",
    userRef,              // target instance
    "java.lang.String",   // value's type name
    "Bob"                 // new value
);

ExecMessage response = thinPeer.sendToPeer(request);
```

Setting an instance field to a remote object:

```java
ExecMessage request = messageBuilder.buildPutObject(
    clientId,
    "com.example.User",
    "address",
    userRef,         // target instance
    addressRef       // ObjectRef to set as value
);
```

### Control Messages

Control messages manage the remote peer's object lifecycle. They return `ControlMessage` instead of `ExecMessage`.

#### Delete an object

Removes a specific object from the remote peer's session:

```java
ControlMessage request = messageBuilder.buildDeleteObjectCommandMessage(
    clientId,
    objectRef     // the object to delete
);

ControlMessage response = thinPeer.sendToPeer(request);
```

#### Delete a session

Removes all objects associated with a client session:

```java
ControlMessage request = messageBuilder.buildDeleteSessionCommandMessage(
    clientId      // session to delete (identified by peer UUID)
);

ControlMessage response = thinPeer.sendToPeer(request);
```

#### Trigger garbage collection

```java
ControlMessage request = messageBuilder.buildGcCommandMessage(clientId);

ControlMessage response = thinPeer.sendToPeer(request);
```

#### Ping

Liveness check. The generic `buildControlCommandMessage` builder is used since there is no dedicated `buildPingCommandMessage` helper:

```java
import io.quasient.pal.messages.types.ControlCommandType;

ControlMessage request = messageBuilder.buildControlCommandMessage(
    clientId, ControlCommandType.PING);

ControlMessage response = thinPeer.sendToPeer(request);
```

The response status is `OK` if the peer is reachable.

> Metadata queries (`meta` method, e.g. `fetch_classes_info`) are exposed on the JSON-RPC channel only; there is no binary-RPC dispatch path for `MetaMessage` requests today. See [JSON-RPC Reference → Meta](rpc-json.md#meta-metadata-query).

## Response Handling

### ExecMessage responses

Every operation (constructor, method call, field access) returns an `ExecMessage` response. The response contains either a return value or a thrown exception --- never both.

```java
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.Unwrapper;

ExecMessage response = thinPeer.sendToPeer(request);

// Check for exceptions first
if (response.getRaisedThrowable() != null
    && response.getRaisedThrowable().getThrowable() != null) {
    String exType = response.getRaisedThrowable().getThrowable().getType();
    String exMessage = response.getRaisedThrowable().getThrowable().getMessage();
    // handle error...
}

// Read the return value
ReturnValue returnValue = response.getReturnValue();
```

#### Void methods

```java
if (returnValue.getIsVoid()) {
    // method returned void — no value to read
}
```

#### Primitive and wrapper return values

Values are serialized inside an `Obj` wrapper. Use `Unwrapper` to deserialize:

```java
Obj obj = returnValue.getObject();
Object value = Unwrapper.unwrapObject(obj);
// value is the deserialized Java object (Integer, String, etc.)
```

#### ObjectRef return values

When a method returns a complex object, the response contains an ObjectRef rather than a serialized value:

```java
Obj obj = returnValue.getObject();
int ref = obj.getRef();
ObjectRef resultRef = ObjectRef.from(ref);
// Use resultRef in subsequent calls
```

#### Null return values

```java
Obj obj = returnValue.getObject();
if (obj.getIsNull()) {
    // method returned null
}
```

#### Array return values

Arrays are returned as serialized values. The type is indicated by the class name (e.g., `[I` for `int[]`, `[Ljava.lang.String;` for `String[]`):

```java
Obj obj = returnValue.getObject();
String typeName = obj.getClazz().getName();  // e.g., "[I" for int[]
Object array = Unwrapper.unwrapObject(obj);  // deserialized array
```

### ControlMessage responses

Control operations return a `ControlMessage` with a status code:

```java
import io.quasient.pal.messages.types.ControlStatusType;

ControlMessage response = thinPeer.sendToPeer(controlRequest);
byte status = response.getStatus();
```

| Status | Constant | Meaning |
|--------|----------|---------|
| 1 | `OK` | Command executed successfully |
| 2 | `ERROR` | Command caused an error |
| 3 | `UNAUTHORIZED` | Peer is not authorized |
| 4 | `UNSUPPORTED` | Command is not supported |
| 5 | `NO_SUCH_SESSION` | Session does not exist |
| 6 | `NO_SUCH_OBJECT` | Object does not exist |

## Argument Passing

### Parallel arrays pattern

MessageBuilder uses parallel arrays to describe method/constructor arguments. For each parameter position `i`:

- `parameterTypes[i]` --- the fully qualified type name (always required)
- `args[i]` --- the value, for primitives, wrappers, and strings
- `argObjRefs[i]` --- the ObjectRef, for remote object arguments

Set one of `args[i]` or `argObjRefs[i]` to a value, and the other to `null`. If both are `null`, the argument is treated as `null`.

```java
// Method signature: process(String name, int count, List items)
//   "name" is a string, "count" is a primitive, "items" is a remote object

String[] types = {"java.lang.String", "int", "java.util.List"};
Object[] args = {"Alice", 5, null};               // null for ObjectRef params
ObjectRef[] refs = {null, null, itemsListRef};     // null for value params
```

### Supported value types

Values passed in the `args` array can be:

- **Primitives**: `int`, `long`, `double`, `float`, `short`, `byte`, `char`, `boolean`
- **Wrappers**: `Integer`, `Long`, `Double`, `Float`, `Short`, `Byte`, `Character`, `Boolean`
- **Strings**: `String`
- **Null**: `null` (with the type name still provided in `parameterTypes`)
- **Collections**: `ArrayList`, `HashMap` (serialized by value)
- **Arrays**: e.g., `String[]` (type name: `[Ljava.lang.String;`)

### Collections passed by value

Collections like `ArrayList` and `HashMap` are serialized and sent by value (not by reference):

```java
ArrayList<Integer> numbers = new ArrayList<>();
Collections.addAll(numbers, 39, 5, 58, 32, 70, 42);

ExecMessage request = messageBuilder.buildClassMethod(
    clientId,
    "com.example.Service",
    "sumList",
    new String[]{"java.util.ArrayList"},
    null, null,
    new Object[]{numbers},       // ArrayList serialized by value
    new ObjectRef[]{null}
);
```

### Array type names

Array type names follow Java's internal naming convention:

| Java type | Type name |
|-----------|-----------|
| `String[]` | `[Ljava.lang.String;` |
| `int[]` | `[I` |
| `double[]` | `[D` |
| `long[]` | `[J` |
| `boolean[]` | `[Z` |
| `byte[]` | `[B` |
| `char[]` | `[C` |
| `float[]` | `[F` |
| `short[]` | `[S` |

## Error Handling

When a remote operation throws an exception, the response `ExecMessage` contains a `RaisedThrowable` instead of a `ReturnValue`:

```java
ExecMessage response = thinPeer.sendToPeer(request);

if (response.getRaisedThrowable() != null
    && response.getRaisedThrowable().getThrowable() != null) {

    var throwable = response.getRaisedThrowable().getThrowable();
    String type = throwable.getType();        // e.g., "java.lang.NoSuchMethodException"
    String message = throwable.getMessage();  // exception message
    // throwable also has getStackTraceElements() and getCause()
}
```

Common exceptions:

| Exception | When |
|-----------|------|
| `ClassNotFoundException` | Class name does not exist on the remote peer |
| `NoSuchMethodException` | No matching method/constructor signature found |
| `NoSuchFieldException` | Field name does not exist on the class |
| `NullPointerException` | Method called on an ObjectRef that no longer exists, or null argument where non-null required |
| `IllegalArgumentException` | Wrong value type for a field put operation |
| `NumberFormatException` | String-to-number conversion failed |
| `RuntimeException` | Application-level exception thrown by the remote method |

## Sending via Log

In addition to direct peer-to-peer RPC, messages can be sent through a log. The ThinPeer handles this transparently for both Kafka and Chronicle backends — whichever is configured on the ThinPeer's input/output logs:

```java
// Direct peer-to-peer (synchronous, low latency)
ExecMessage response = thinPeer.sendToPeer(request);

// Via log, synchronous: append the request to the log and poll for a response
LogMessage<Message> responseLogMessage =
    thinPeer.sendExecMessageToLogAndReceive(request);
ExecMessage response = responseLogMessage.getContent().getExecMessage();

// Via log, fire-and-forget: append the request and return immediately
thinPeer.sendExecMessageToLog(request);
```

The log path writes the message to the configured backend (Kafka topic or Chronicle queue), where the target peer consumes and executes it; for the synchronous variant, the response travels back through the log and is matched by request ID. This is useful when messages need to be persisted or when the consumer may not be running yet.

## Binary vs JSON-RPC

Both protocols support the same operations. Choose based on your requirements:

| | Binary RPC | JSON-RPC |
|--|-----------|----------|
| **Speed** | Microsecond-range | Higher latency (text parsing overhead) |
| **Wire size** | Compact binary (Colfer) | Larger (JSON text) |
| **Language support** | Wire format is language-agnostic (Colfer + ZeroMQ have multi-language bindings); PAL ships only a Java client today | Any language with a JSON + WebSocket library |
| **Debugging** | Opaque bytes | Readable messages |
| **Multi-step workflows** | Manual ObjectRef tracking | [RpcChain DSL](rpc-chain.md) available |
| **Transport** | ZeroMQ (TCP) | WebSocket |

Use binary RPC where performance matters and a Java client fits. Use JSON-RPC for tooling, debugging, or quickly wiring up non-Java callers without writing a Colfer client first.

## Further Reading

- [Remote Procedure Calls](rpc.md) — RPC overview and how the formats relate
- [JSON-RPC Reference](rpc-json.md) — Wire-format and Java factory for JSON-RPC
- [RpcChain DSL](rpc-chain.md) — Java DSL for multi-step JSON-RPC workflows with automatic ObjectRef tracking
- [RPC Policy](rpc-policy.md) — Access control for RPC operations
- [Peers and Logs](peers-and-logs.md) — Understanding peers and ThinPeer
