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

PAL's specific contribution is *retrofitting* message-passing onto existing Java code through post-compile bytecode weaving, without requiring developers to adopt a new programming model, language, or framework. This comes with trade-offs (see [Trade-offs and Limitations](concepts/trade-offs.md)) but enables incremental adoption: you can add PAL to an existing project and selectively use its capabilities without rewriting code.

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

The CLI is one way to make RPC calls. PAL also provides programmatic APIs at different levels of abstraction—see [Making RPC Calls](concepts/rpc.md#making-rpc-calls), [JsonRpcMessageFactory](concepts/rpc.md#jsonrpcmessagefactory), and the [RpcChain DSL](concepts/rpc-chain.md).

RPC is explicit—you target a specific peer by name or UUID. There is no transparent location independence; you always know when a call crosses a network boundary.

**Enables:**

- **Cross-peer invocation:** Call any method on a remote peer by name—no service definitions or code generation required
- **Intercept callbacks:** Intercepts on one peer can trigger callbacks on another, enabling cross-peer behavior modification
- **Development and operational workflows:** Invoke methods, inspect state, and test behavior on running peers, programmatically or from the CLI

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

- **BEFORE:** Callback runs before method, synchronously (blocks)
- **AFTER:** Callback runs after method, synchronously
- **AROUND:** Callback can replace method entirely, return different result
- **BEFORE_ASYNC:** Fire-and-forget BEFORE—callback is sent but execution proceeds without waiting for a response (cannot mutate arguments)
- **AFTER_ASYNC:** Fire-and-forget AFTER—callback is sent but execution proceeds without waiting (cannot override return value)

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
(70 bytes, 2μs to serialize)

// JSON-RPC
{
  "jsonrpc": "2.0",
  "method": "Calculator.add",
  "params": [5, 3],
  "id": 1
}
(85 bytes, 50μs to serialize)
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

- **Build configuration**: Your Maven or Gradle build adds the AspectJ weaving plugin and declares `pal-weave` as an aspect library dependency
- **Compiled `.class` files**: Woven bytecode contains PAL dispatch calls that depend on PAL's runtime aspects
- **Reversibility**: Removing the weaving plugin and rebuilding produces standard Java classes that work without PAL
- **Nature of the trade-off**: This is a build-time opt-in, not a source-code-level dependency. Your `.java` files never import PAL, but your `.class` files contain PAL's weaving

## How PAL Relates to Other Technologies

PAL builds on ideas from message-passing systems (Smalltalk, Erlang, Akka) but differs in approach: rather than requiring you to adopt a new programming model, PAL retrofits message-passing onto existing Java code via build-time weaving. This means you don't rewrite code to an actor model or define service contracts—but it also means your compiled `.class` files depend on PAL's runtime aspects, trading a build-time dependency for transparency and flexibility.

PAL's RPC is not a replacement for purpose-built RPC frameworks like gRPC, which offer schema evolution, code generation, and strong typing. PAL's RPC serves a different purpose: it enables intercept callbacks between peers, supports development and debugging workflows, and provides operational tooling for PAL-managed applications.

PAL's interception system is more dynamic than traditional AspectJ usage. Where AspectJ aspects are typically defined in application code and woven at build time, PAL's intercepts are registered at runtime via a directory service and can be added or removed without recompilation.

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

## Performance Considerations

**Performance characteristics** depend heavily on configuration (which features are enabled) and workload. PAL adds overhead at each dispatch point—the magnitude depends on whether WAL writing, PUB publishing, or intercept matching is active. With no features enabled (standalone mode), overhead is primarily the AspectJ dispatch cost. See the [JVM Configuration](guides/jvm-configuration.md) guide for tuning options. Benchmark results will be published separately.

For a detailed discussion of overhead sources and mitigation strategies, see [Trade-offs and Limitations](concepts/trade-offs.md).

## Next Steps

Now that you understand PAL's core concepts:

1. **Get hands-on:** Follow the [Getting Started](getting-started.md) tutorial
2. **Explore use cases:** Read [Use Cases](use-cases.md) for your role
3. **Learn the details:**
   - [Peers and Logs](concepts/peers-and-logs.md) - Core entities
   - [RPC](concepts/rpc.md) - Remote procedure calls
   - [Interception](concepts/interception.md) - Dynamic behavior modification
   - [Logs](concepts/logs.md) - Chronicle vs Kafka
4. **Try it yourself:** Work through the [Local Development Guide](guides/local-development.md)
