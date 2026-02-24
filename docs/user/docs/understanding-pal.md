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
// The operation persists, can be replayed, routed, intercepted
```

Same code, but now every operation is a first-class entity. This has profound implications.

## Quantization: Operations Become Messages

PAL quantizes execution into discrete messages. Every constructor call, method invocation, and field access becomes a serializable event.

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
│                                                          │
│  Your Java classes are compiled with AspectJ aspects    │
│  that insert interception points at every operation.    │
│                                                          │
│  YourClass.java ──AspectJ──► YourClass.class (woven)    │
└─────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  2. Runtime: Message Creation                           │
│                                                          │
│  When your code runs, each operation:                   │
│  - Triggers the woven interception point                │
│  - Captures operation metadata (class, method, args)    │
│  - Creates an ExecMessage                               │
│  - Passes through PAL runtime for processing            │
│                                                          │
│  Operation ──Intercept──► ExecMessage ──Process──► ...  │
└─────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  3. Message Processing                                   │
│                                                          │
│  The ExecMessage can now be:                            │
│  - Logged to write-ahead log (WAL)                      │
│  - Routed to remote peer (RPC)                          │
│  - Intercepted by callbacks (interception)              │
│  - Executed locally via reflection                      │
│                                                          │
│  ExecMessage ───┬──► WAL                                │
│                 ├──► Remote Peer                        │
│                 ├──► Intercept Callback                 │
│                 └──► Local Execution                    │
└─────────────────────────────────────────────────────────┘
```

## Why Operations as Messages?

Once operations are reified as messages, they become first-class entities that can be manipulated, stored, and transmitted. This unlocks capabilities that are fundamentally impossible when operations are ephemeral.

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
pal print -l payment-log --full
// See exact sequence: charge() → validate() → authorize() → capture()

// Or replay to reproduce a bug
pal run --source-log payment-log -cp app.jar
```

**Enables:**

- **Time-travel debugging:** Replay any execution from the beginning
- **Audit trails:** Every operation is recorded for compliance
- **Event sourcing:** State reconstructed from operation log
- **Root cause analysis:** See exact sequence that led to a bug

### Messages Can Be Routed

**Standard JVM:**
```java
// Objects must be in the same JVM
Calculator calc = new Calculator();
int result = calc.add(2, 3);  // Local only
```

**With PAL:**
```java
// Objects can be on remote peers, called transparently
Calculator calc = ...; // May be remote, code doesn't know or care
int result = calc.add(2, 3);  // RPC happens transparently

// The message routes to the peer hosting Calculator
ExecMessage {method: "add", args: [2, 3]} ──Network──► Peer B
```

**Enables:**

- **Transparent RPC:** Call remote methods as if local, no stubs or proxies
- **Distributed systems:** Objects communicate across network boundaries
- **Actor patterns:** Asynchronous messaging with normal objects
- **Location transparency:** Objects can move between peers

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
InterceptRequest<InterceptableMethodCall> intercept =
    new InterceptRequest<>(
        UUID.randomUUID(),
        myCallbackPeer,
        InterceptType.AROUND,
        "com.example.OrderService",
        "com.example.DiscountOverride",
        "applyNewDiscount",
        new InterceptableMethodCall(
            "calculateDiscount", Arrays.asList("com.example.Order")));

// Now when calculateDiscount() is called:
// 1. Message created: {method: "calculateDiscount", args: [order]}
// 2. Intercept matched
// 3. Callback peer invoked
// 4. Callback can: inspect args, replace result, skip execution
```

**Enables:**

- **Hot-patching production:** Fix bugs without restart or redeploy
- **Testing without mocks:** Replace implementations dynamically
- **A/B testing:** Route some requests to new implementation
- **Feature flags:** Enable/disable features at message level
- **Monitoring:** Observe every call without instrumentation

## The Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                     Your Application                          │
│                                                               │
│  OrderService.java    PaymentService.java    Account.java    │
│  (Normal Java code, no PAL dependencies)                      │
└────────────────────────┬──────────────────────────────────────┘
                         │
                         │ At build time: AspectJ weaving
                         │
                         ▼
┌───────────────────────────────────────────────────────────────┐
│                      PAL Runtime                              │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  Dispatch   │  │ Serialization│  │   Transport         │ │
│  │             │  │              │  │                     │ │
│  │ • Method    │  │ • Colfer     │  │ • ZeroMQ (RPC)     │ │
│  │ • Field     │  │ • JSON-RPC   │  │ • Kafka (async)    │ │
│  │ • Ctor      │  │              │  │ • Chronicle (local)│ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │Interception │  │   Logging    │  │   Directory         │ │
│  │             │  │              │  │                     │ │
│  │ • Pattern   │  │ • WAL write  │  │ • Peer registry    │ │
│  │   matching  │  │ • WAL read   │  │   (etcd)           │ │
│  │ • Callbacks │  │ • Replay     │  │ • Service discovery│ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
                         │
                         │
                         ▼
┌───────────────────────────────────────────────────────────────┐
│                   Infrastructure                              │
│                                                               │
│  etcd (directory)    Kafka (logs)    Chronicle (local logs)  │
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
│  UUID: 550e8400-e29b-41d4-a716-... │
│  Name: "payment-service"            │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  Your Application             │ │
│  │  PaymentService, OrderService │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  PAL Runtime                  │ │
│  │  Message routing, logging     │ │
│  └───────────────────────────────┘ │
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

**Kafka logs (distributed):**
```
Topic: order-service-wal
├── Message 0: OrderService.create(order1)
├── Message 1: OrderService.validate(order1)
├── Message 2: PaymentService.charge(card, 100)
├── Message 3: OrderService.confirm(order1)
└── Message 4: NotificationService.send(email)
```

**Chronicle logs (local):**
```
Path: /tmp/order-service-wal/
├── 20231109.cq4     (memory-mapped file)
│   └── Messages 0-1000000
├── 20231109-01.cq4
│   └── Messages 1000001-2000000
└── metadata.cq4t
```

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
- **AFTER:** Callback runs after method, asynchronously
- **AROUND:** Callback can replace method entirely, return different result

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
- Interoperable (any language can call)
- Debuggable (inspect with standard tools)
- Best for external integrations

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
# Compile with AspectJ weaving
mvn compile  # aspectj-maven-plugin configured

# Run with PAL
pal run --wal payment-log --json-rpc auto -cp app.jar Main

# Now:
# - Every method call is logged to payment-log
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

## Comparison to Other Approaches

### PAL vs Akka

**Akka (explicit actors):**
```java
// Akka requires actor model
class PaymentActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ChargeMessage.class, msg -> {
                // Handle message explicitly
            })
            .build();
    }
}

// Send message explicitly
paymentActor.tell(new ChargeMessage(card, amount), self());
```

**PAL (transparent actors):**
```java
// Normal Java class
class PaymentService {
    public void charge(Card card, double amount) {
        // Business logic
    }
}

// Call normally, messaging is transparent
paymentService.charge(card, amount);
// PAL converts this to message internally
```

**Key difference:** Akka requires rewriting code to actor model. PAL works with normal objects.

### PAL vs AspectJ Alone

**AspectJ (requires aspects in application):**
```java
// Application must define aspects
@Aspect
public class LoggingAspect {
    @Around("execution(* com.example.*.*(..))")
    public Object logMethod(ProceedingJoinPoint pjp) {
        // Logging logic in application code
    }
}
```

**PAL (aspects provided by runtime):**
```java
// Application just uses PAL runtime
// No aspects in application code
// PAL's aspects intercept everything
```

**Key difference:** AspectJ requires application to define aspects. PAL provides aspects as runtime service.

### PAL vs gRPC/REST

**gRPC (explicit service definitions):**
```protobuf
// Define service in .proto file
service PaymentService {
  rpc Charge (ChargeRequest) returns (ChargeResponse);
}

// Generate stubs
// Implement server interface
// Client uses generated stub
```

**PAL (no service definitions needed):**
```java
// Just normal Java class
class PaymentService {
    public void charge(Card card, double amount) { ... }
}

// Automatically available for RPC
// No .proto, no generation, no stubs
```

**Key difference:** gRPC requires explicit service definitions and code generation. PAL discovers methods via reflection.

## When to Use PAL

**Use PAL when you want:**

- Testing without writing mocks
- Production debugging with replay
- Distributed systems without RPC boilerplate
- Event sourcing without explicit events
- Hot-patching without redeploy
- Audit trails without instrumentation

**Don't use PAL when:**

- You need extreme performance (PAL adds overhead)
- You're working with non-Java languages (PAL is Java-only)
- Your application is small/simple (overhead not worth it)
- You don't need any of PAL's capabilities

## Performance Considerations

PAL adds overhead at each stage:

| Stage | Overhead | Impact |
|-------|----------|--------|
| Weaving (build time) | One-time | Slightly larger .class files |
| Interception (runtime) | 10-50ns per call | Negligible for most apps |
| Message creation | 100-500ns per call | Noticeable for hot loops |
| Serialization (Colfer) | 1-10μs | Acceptable for RPC |
| Serialization (JSON) | 50-200μs | Use for external clients only |
| Kafka write | 1-10ms (async batched) | Don't use in critical path |
| Chronicle write | 100ns-1μs | Fast enough for most logging |
| RPC (ZeroMQ) | 10-100μs (local network) | Similar to gRPC |

**General guidance:**

- For CPU-bound tight loops: Consider selective weaving (don't intercept math operations)
- For I/O-bound services: PAL overhead is negligible compared to I/O
- For RPC: Performance similar to gRPC or Thrift
- For logging: Chronicle is fast enough for most use cases

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
