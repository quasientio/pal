# Understanding PAL

## The Big Idea

Standard JVMs execute operations and discard them immediately:

```java
calculator.add(2, 3);  // Happens, then gone forever
```

PAL reifies operations as messages:

```java
calculator.add(2, 3);
// → ExecMessage { class: "Calculator", method: "add", args: [2, 3] }
// The operation persists, can be logged, replayed, intercepted
```

Same code, but now every operation is a first-class entity. This has profound implications.

## Prior Art

The principle of treating operations as messages is foundational to computing. Smalltalk (1970s) pioneered "everything is a message." Erlang/OTP built a production-grade ecosystem on message-passing and process isolation. Akka brought actor-model message-passing to the JVM. Dapr provides a sidecar-based approach for polyglot systems.

PAL's specific contribution is *retrofitting* message-passing onto existing Java code through post-compile bytecode weaving, without requiring developers to adopt a new programming model, language, or framework. This comes with trade-offs (see [Trade-offs and Limitations](#trade-offs-and-limitations) below) but enables incremental adoption: you can add PAL to an existing project and selectively use its capabilities without rewriting code.

## Quantization: How Operations Become Messages

PAL quantizes execution by instrumenting every constructor call, method invocation, and field access at build time via AspectJ. At runtime, these instrumented operations become messages when logging, interception, or publishing is enabled.

### What Gets Quantized

**Method calls:**
```java
orderService.processOrder(order, customer);

// Becomes:
ExecMessage {
  class: "OrderService"
  method: "processOrder"
  args: [order, customer]
  peer: "peer-uuid-123"
  timestamp: 1699564800000
}
```

**Field access:**
```java
account.balance = 1000;

// Becomes:
ExecMessage {
  class: "Account"
  field: "balance"
  value: 1000
  operationType: FIELD_SET
  peer: "peer-uuid-123"
  timestamp: 1699564800001
}
```

**Constructors:**
```java
Payment payment = new Payment(amount, currency);

// Becomes:
ExecMessage {
  class: "Payment"
  constructor: "<init>"
  args: [amount, currency]
  peer: "peer-uuid-123"
  timestamp: 1699564800002
}
```

### How Quantization Works

```
┌─────────────────────────────────────────────────────────┐
│  1. Build Time: AspectJ Weaving                         │
│                                                         │
│  Your Java classes are compiled with AspectJ aspects    │
│  that insert interception points at every operation.    │
│                                                         │
│  YourClass.java ──AspectJ──► YourClass.class (woven)    │
└─────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  2. Runtime: Conditional Message Creation               │
│                                                         │
│  When your code runs, each instrumented operation:      │
│  - Triggers the woven interception point                │
│  - If logging, publishing, or interception is active    │
│    and the operation is within recording scope:         │
│    creates an ExecMessage for processing                │
│  - Otherwise: executes with minimal overhead            │
│                                                         │
│  Operation ──Dispatch──► [Config+Scope?] ──► Message    │
└─────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  3. Message Processing                                  │
│                                                         │
│  The ExecMessage can now be:                            │
│  - Logged to write-ahead log (WAL)                      │
│  - Published via a ZMQ socket (PUB)                     │
│  - Intercepted by callbacks (interception)              │
│  - Executed locally                                     │
│                                                         │
│  ExecMessage ───┬──► WAL                                │
│                 ├──► Remote Peer (SUB)                  │
│                 ├──► Intercept Callback                 │
│                 └──► Local Execution                    │
└─────────────────────────────────────────────────────────┘
```

## Why Operations as Messages?

Once operations are reified as messages, they become first-class entities that can be manipulated, stored, and transmitted. This unlocks capabilities that are not available when operations are ephemeral.

### Messages Can Be Logged

**Standard JVM:**
```java
// Execution happens, state changes, no record
paymentService.charge(card, 100.00);
// How did we get here? What was the sequence? Unknown.
```

**With PAL:**
```java
// Every operation written to durable log
paymentService.charge(card, 100.00);

// Later: Replay the entire execution
pal log print payment-log --full
// See exact sequence: charge() → validate() → authorize() → capture()

// Or replay to reproduce a bug
pal run --source-log payment-log -cp app.jar
```

**Enables:**

- **Time-travel debugging:** Replay any execution from the beginning
- **Audit trails:** Every operation is recorded for compliance
- **Event sourcing:** State reconstructed from operation log
- **Root cause analysis:** See exact sequence that led to a bug

### Messages Can Be Sent to Remote Peers

**Standard JVM:**
```java
// To call a method on a remote service, you need:
// - Service definitions (gRPC .proto, REST endpoints)
// - Generated client code or HTTP clients
// - Explicit serialization/deserialization
```

**With PAL:**
```bash
# Invoke a method on a remote peer by name
pal peer call calculator com.example.Calculator add 2 3

# Or with full control via JSON-RPC on stdin
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{
  "type":"com.example.Calculator","method":"add",
  "args":[{"type":"int","value":2},{"type":"int","value":3}]
}}' | pal peer call calculator

# PAL sends an ExecMessage to the target peer:
# ExecMessage {method: "add", args: [2, 3]} ──Network──► Peer "calculator"
# Target peer executes message, returns result
```

The CLI is one way to make RPC calls. PAL also provides programmatic APIs at different levels of abstraction—see [Making RPC Calls](concepts/rpc.md#making-rpc-calls), [JsonRpcMessageFactory](concepts/rpc-json.md#jsonrpcmessagefactory), and the [RpcChain DSL](concepts/rpc-chain.md).

RPC is explicit—you target a specific peer by name or UUID. There is no transparent location independence; you always know when a call crosses a network boundary.

**Enables:**

- **Cross-peer invocation:** Call any method on a remote peer by name—no service definitions or code generation required.
- **Intercept callbacks:** Intercepts on one peer can trigger callbacks on another, enabling cross-peer behavior modification.
- **Development and operational workflows:** Invoke methods, inspect state, and test behavior on running peers, programmatically or from the CLI.

### Messages Can Be Intercepted

**Standard JVM:**
```java
// Behavior is fixed at compile time
public int calculateDiscount(Order order) {
    return order.total() * 10 / 100;  // 10% hardcoded
}
// To change: recompile, redeploy, restart
```

**With PAL:**
```java
// Behavior can be modified at runtime
// Original code unchanged
public int calculateDiscount(Order order) {
    return order.total() * 10 / 100;
}

// But at runtime, register an intercept:
InterceptRequest<InterceptableMethodCall> discountOverride =
    new InterceptRequest<>(
        UUID.randomUUID(),
        discountServicePeerUuid,
        InterceptType.AROUND,
        "com.example.OrderService",
        "com.example.DiscountOverride",
        "applyNewDiscount",
        new InterceptableMethodCall(
            "calculateDiscount", Arrays.asList("com.example.Order")));

// The callback on the intercepting peer:
public class DiscountOverride {
    public static InterceptCallbackResponse applyNewDiscount(InterceptContext ctx) {
        // Override the 10% discount with 20%
        Order order = (Order) ctx.getArgs()[0];
        ctx.setReturnValue(order.total() * 20 / 100);
        return InterceptCallbackResponse.skipProceed();
    }
}

// Now when calculateDiscount() is called:
// 1. Intercept matched
// 2. Callback peer invoked
// 3. applyNewDiscount() skips original method, returns 20% discount
```

**Enables:**

- **Hot-patching production:** Fix bugs without restart or redeploy
- **Testing without mocks:** Replace implementations dynamically
- **A/B testing:** Redirect some requests to a new implementation
- **Feature flags:** Enable/disable features at message level
- **Monitoring:** Observe every call without instrumentation

Because intercept callbacks can mutate arguments, skip execution, or override return values, interception serves as a **general-purpose primitive**. Higher-level patterns like:

- **routing:** e.g. dispatch calls to the right peer
- **filtering:** e.g. reject calls based on arguments
- **transformation:** e.g. rewrite arguments or return values
- **caching:** e.g. return stored results without executing

...just to name a few can all be built on top of it.

Because callbacks live on a *separate* peer rather than alongside the target, this is **networked AOP**: cross-cutting concerns are delivered by other processes (potentially on other machines) rather than by code woven into the target's JVM. It is the same shape of horizontal extension a service mesh provides for network requests, applied here at the operation level — method calls, constructor invocations, and field reads/writes — rather than at the network layer.

## The Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                     Your Application                          │
│                                                               │
│  OrderService.java    PaymentService.java    Account.java     │
│  (Normal Java code, no PAL dependencies)                      │
└────────────────────────┬──────────────────────────────────────┘
                         │
                         │ At build time: AspectJ weaving
                         │
                         ▼
┌───────────────────────────────────────────────────────────────┐
│                      PAL Runtime                              │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐   │
│  │  Dispatch   │  │ Serialization│  │   Transport         │   │
│  │             │  │              │  │                     │   │
│  │ • Method    │  │ • Colfer     │  │ • ZeroMQ (RPC)      │   │
│  │ • Field     │  │ • JSON-RPC   │  │ • Kafka (async)     │   │
│  │ • Ctor      │  │              │  │ • Chronicle (local) │   │
│  └─────────────┘  └──────────────┘  └─────────────────────┘   │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐   │
│  │Interception │  │   Logging    │  │   Directory         │   │
│  │             │  │              │  │                     │   │
│  │ • Pattern   │  │ • WAL write  │  │ • Peer registry     │   │
│  │   matching  │  │ • WAL read   │  │   (etcd)            │   │
│  │ • Callbacks │  │ • Replay     │  │ • Service discovery │   │
│  └─────────────┘  └──────────────┘  └─────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                         │
                         │
                         ▼
┌───────────────────────────────────────────────────────────────┐
│                   Infrastructure                              │
│                                                               │
│  etcd (directory)    Kafka (logs)    Chronicle (local logs)   │
└───────────────────────────────────────────────────────────────┘
```

### Three Layers

**1. Application Layer**

- Your normal Java code
- No PAL dependencies
- No annotations or special interfaces
- Works with or without PAL

**2. PAL Runtime Layer**

- Converts operations to messages (quantization)
- Routes messages (local, remote, or logged)
- Manages interception (dynamic callbacks)
- Handles serialization (binary or JSON)
- Coordinates peers (directory service)

**3. Infrastructure Layer**

- **etcd:** Distributed key-value store for peer registry and intercepts
- **Kafka:** Distributed logs for message persistence and pub/sub
- **Chronicle Queue:** Local memory-mapped files for high-performance logging

## Core Concepts

### Peers

A peer is a PAL runtime instance that executes your code.

```
┌─────────────────────────────────────┐
│  Peer (process)                     │
│                                     │
│  UUID: 550e8400-e29b-41d4-a716-...  │
│  Name: "payment-service"            │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Your Application             │  │
│  │  PaymentService, OrderService │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  PAL Runtime                  │  │
│  │  Message dispatch, logging    │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

Peers can:

- Send messages to other peers (RPC)
- Receive messages from other peers
- Write messages to logs (WAL)
- Read messages from logs (replay)
- Register intercepts (dynamic behavior)
- Be discovered via directory (etcd)

### Logs

Logs are durable, ordered sequences of messages.

**Two backends, same output—only the command differs:**
```bash
# Chronicle (local, no infrastructure)
pal log print file:/tmp/hello-world.wal --tree

# Kafka (distributed)
pal log print -k localhost:29092 hello-world-wal --tree
```

```
[0] call HelloWorld.main
  [1] get System.out
  [2] return PrintStream@1 (out)
  [3] call PrintStream.println@1
  [4] return void
[5] return void
```

Other output formats are available (`--full`, `--json`). See [CLI Reference](cli-reference.md#pal-log-print-print-messages-from-a-log) for details.

Logs enable:

- **Replay:** Re-run execution from log
- **Debugging:** Inspect what happened
- **Audit:** Compliance record of all operations
- **Event sourcing:** Reconstruct state from events

### Interception

Intercepts are dynamic callbacks registered at runtime.

```java
// Register an intercept
InterceptRequest<InterceptableMethodCall> intercept =
    new InterceptRequest<>(
        UUID.randomUUID(),                   // intercept ID
        monitorPeerUuid,                     // callback peer
        InterceptType.BEFORE,                // type
        "com.example.OrderService",          // class to intercept
        "com.example.OrderMonitor",          // callback class
        "onBeforeProcessOrder",              // callback method
        new InterceptableMethodCall(
            "processOrder", Arrays.asList("com.example.Order")));
palDirectory.createIntercept(intercept);

// Now when any peer calls OrderService.processOrder():
// 1. Message created
// 2. Pattern matches
// 3. Callback peer "monitor-peer-uuid" is invoked
// 4. Callback can inspect args, block execution, etc.
```

**Intercept types:**

- **BEFORE:** Callback runs before method, synchronously (blocks).
- **AFTER:** Callback runs after method, synchronously.
- **AROUND:** Callback can replace method entirely, return different result.
- **BEFORE_ASYNC:** Fire-and-forget BEFORE—callback is sent but execution proceeds without waiting for a response (cannot mutate arguments).
- **AFTER_ASYNC:** Fire-and-forget AFTER—callback is sent but execution proceeds without waiting (cannot override return value).

**Pattern matching:**

- **Ant-style patterns:** `com.example.**`, `OrderService.process*`
- **Match class and method:** `OrderService.processOrder`
- **Match all methods:** `OrderService.*`

## Serialization: Messages on the Wire

Messages must be serialized to be sent across network or written to logs.

**Binary format (Colfer):**

- Fast (microsecond serialization)
- Compact (minimal overhead)
- Type-safe schema
- Best for production RPC

**JSON-RPC format:**

- Human-readable
- Debuggable (inspect with standard tools)
- Best for tooling and external integrations

Same operation, two formats:

```java
// Operation
calculator.add(5, 3);

// Colfer (binary)
0x12 0x0A 0x43 0x61 0x6C 0x63 0x75 0x6C 0x61...
(compact binary; microsecond-range serialization)

// JSON-RPC
{
  "jsonrpc": "2.0",
  "method": "Calculator.add",
  "params": [5, 3],
  "id": 1
}
(human-readable text; larger size and slower serialization than Colfer)
```

## Transparency: No Code Changes Required

The most important aspect of PAL: your application code doesn't know PAL exists.

**Standard Java application:**
```java
public class PaymentService {
    public void charge(Card card, double amount) {
        // Business logic
    }
}

public class Main {
    public static void main(String[] args) {
        PaymentService service = new PaymentService();
        service.charge(myCard, 100.00);
    }
}
```

**Same code, running with PAL:**
```bash
# Build with AspectJ weaving
./gradlew build  # weaveClasses task configured

# Run with PAL
pal run --wal payment-log --interceptable --json-rpc auto -cp app.jar Main

# Now:
# - Every method call is logged to a write-ahead log
# - RPC is available (call from other peers)
# - Interception is possible (hot-patch at runtime)
# - But Main.java is unchanged
```

No annotations:
```java
// NOT required
@PALMessage  // ✗
@Interceptable  // ✗
@Loggable  // ✗
```

No interfaces:
```java
// NOT required
class PaymentService implements PALService  // ✗
```

No framework coupling:
```java
// NOT required
import io.quasient.pal.*  // ✗ (in application code)
```

### What Does Change

While your application source code remains unchanged, the build and compiled output do change:

- **Build configuration**: Your Gradle or Maven build adds the AspectJ weaving plugin and declares `pal-weave` as an aspect library dependency.
- **Compiled `.class` files**: Woven bytecode contains PAL dispatch calls that depend on PAL's runtime aspects.
- **Reversibility**: Removing the weaving plugin and rebuilding produces standard Java classes that work without PAL.
- **Nature of the trade-off**: This is a build-time opt-in, not a source-code-level dependency. Your `.java` files never import PAL, but your `.class` files contain PAL's weaving.

## How PAL Relates to Other Technologies

PAL builds on ideas from message-passing systems (Smalltalk, Erlang, Akka) but differs in approach: rather than requiring you to adopt a new programming model, PAL retrofits message-passing onto existing Java code via build-time weaving. This means you don't rewrite code to an actor model or define service contracts—but it also means your compiled `.class` files depend on PAL's runtime aspects, trading a build-time dependency for transparency and flexibility.

PAL's RPC is not a replacement for purpose-built RPC frameworks like gRPC, which offer schema evolution, code generation, and strong typing. PAL's RPC serves a different purpose: it enables intercept callbacks between peers, supports development and debugging workflows, and provides operational tooling for PAL-managed applications.

PAL's interception system differs from traditional AspectJ in two ways together: it is **runtime-registered** (through the directory service rather than baked into the build) and **networked** (the advice runs on a separate peer, communicating with the target via RPC). That combination opens space for cross-cutting concerns historically delivered by service-mesh sidecars — telemetry, authorization, rate limiting, A/B routing, fault injection — but applied at operation granularity (method calls, constructor invocations, and field accesses) rather than network granularity. Add or remove these concerns at runtime without redeploying the target.

## When to Use PAL

All PAL features are off by default. Weaving is a build-time step, but at runtime nothing is enabled unless you explicitly turn it on: WAL logging, interception, publishing, and RPC are each independent flags. This means adopting PAL carries no runtime cost for features you don't use, and you can enable capabilities incrementally as you need them.

**Use PAL when you want:**

- Testing without writing mocks
- Production debugging with replay
- Distributed systems without RPC boilerplate
- Event sourcing without explicit events
- Hot-patching without redeploy
- Audit trails without instrumentation

**Don't use PAL when:**

- You don't need any of PAL's capabilities

## Trade-offs and Limitations

Every engineering decision involves trade-offs. This section documents PAL's honestly, so you can make an informed decision about whether PAL is right for your use case.

### Build-Time Requirements

- AspectJ weaving must be configured in the build (Gradle or Maven plugin).
- Woven `.class` files are larger than originals.
- Build time increases slightly due to the weaving phase.
- The `pal-weave` artifact must be declared as an aspect library dependency.

### Runtime Overhead

Every quantized operation passes through PAL's dispatch layer. The magnitude of overhead depends on which features are enabled:

- **Standalone mode** (no WAL, no intercepts): Overhead is primarily the AspectJ dispatch cost.
- **With WAL writing**: Adds I/O cost per operation (Chronicle Queue is nanosecond-range; Kafka adds millisecond-range async batched writes).
- **With intercepts active**: Adds pattern matching cost per operation, plus possible RPC round-trip for remote callbacks.

For CPU-bound tight loops where per-call overhead matters, consider excluding hot-path classes from weaving via AspectJ pointcut configuration. See the [JVM Configuration](jvm-configuration.md) reference for tuning options. Benchmark results will be published separately.

Stack traces include PAL dispatch frames, which adds noise when debugging. This is inherent to the AspectJ weaving approach.

### Serialization Constraints

- Not all Java objects are serializable; PAL handles primitives, strings, and simple arrays natively.
- Complex objects passed through RPC or intercept callbacks use ObjectRef (peer-local references) rather than full serialization.
- Lambdas, `ThreadLocal` state, `InputStream`s, and objects with circular references cannot be transparently serialized.
- WAL replay with `STUB_FROM_WAL` can reconstruct primitives and strings; complex objects become "phantoms" (operations on them are also stubbed).

### Interception Limitations

For interception-specific constraints — woven-code requirement, pattern syntax, no type-hierarchy matching, serialization limits, and the network round-trip cost of remote callbacks — see [Interception → Limitations](concepts/interception.md#limitations) and [Interception → Performance Impact](concepts/interception.md#performance-impact).

### Log Backend Constraints

Kafka WAL: PAL writes to and reads from a single partition (partition 0) per Kafka topic. This guarantees strict total ordering of the operation stream within a topic, but bounds per-topic throughput to single-partition performance. Scale aggregate throughput by adding more topics — typically one WAL per peer — rather than by partitioning a single topic. See [Single-Partition Design](concepts/logs.md#single-partition-design) for details.

### Scope

- **Java only**: PAL is designed for Java. JVM languages like Kotlin and Scala may work with AspectJ but are not tested.
- **Single-language message format**: PAL's native message format is Java-specific; cross-language integration uses the JSON-RPC protocol.

### What PAL Is Not

- **Not a replacement for production RPC frameworks** (gRPC, REST): PAL's RPC is auxiliary infrastructure supporting intercept callbacks, development workflows, testing, and operational tooling. For production inter-service communication requiring type safety, schema evolution, and high-throughput optimization, use purpose-built RPC frameworks alongside PAL.
- **Not a replacement for APM/observability platforms** (Datadog, New Relic, etc.): PAL captures operations at a different granularity and for different purposes. The WAL is an operation log, not a metrics/tracing pipeline.
- **Not a service mesh in the traditional sense**: PAL does not proxy network traffic, so it does not provide network-layer features like load balancing, mTLS termination, or service-to-service routing policies. However, many of the *outcomes* a service mesh delivers — telemetry, authorization, rate limiting, fault injection, A/B routing — can be achieved through PAL's networked interception, applied at the operation level (method calls, constructor invocations, field reads, field writes) rather than the network-request level. See [Interception](concepts/interception.md).

### Failure Modes

Understanding what happens when infrastructure becomes unavailable:

- **etcd unreachable at startup**: Peer exits immediately with exit code 14 (fail-fast). The peer does not start in degraded mode.
- **Kafka unreachable at startup**: Peer exits immediately with exit code 7 (fail-fast).
- **etcd goes down while peer is running**: Peer's lease expires after TTL (60s default). Intercepts stop updating. Peer continues executing but cannot register new intercepts or be discovered.
- **Kafka goes down while peer is running**: WAL writes will back-pressure. Behavior depends on Kafka producer configuration (blocking vs. dropping).
- **Standalone mode** (no etcd, no Kafka): Peer runs normally with only Chronicle Queue or no logging. This is the simplest and most resilient mode.

## Next Steps

Now that you understand PAL's core concepts:

1. **Get hands-on:** Follow the [Getting Started](getting-started.md) tutorial.
2. **Explore use cases:** Read [Use Cases](use-cases.md) for your role.
3. **Learn the details:**
   - [Peers and Logs](concepts/peers-and-logs.md) - Core entities
   - [RPC](concepts/rpc.md) - Remote procedure calls
   - [Interception](concepts/interception.md) - Dynamic behavior modification
   - [Logs](concepts/logs.md) - Chronicle vs Kafka
4. **Try it yourself:** Work through the [Local Development Guide](guides/local-development.md).
