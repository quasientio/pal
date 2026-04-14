![PAL Logo](assets/imgs/PAL-logo-01.png)

# Welcome to PAL

## The Core Insight

PAL instruments every Java operation at build time. At runtime, instrumented operations become messages when you enable logging, interception, or publishing.

```
Your Code              PAL Runtime                Messages

calculator.add(2, 3)   ──────────►   ExecMessage {
                                       class: "Calculator"
                                       method: "add"
                                       args: [2, 3]
                                     }
                                              │
                       ┌──────────────────────┼──────────────────────┐
                       ▼                      ▼                      ▼
                    LOGGED                 REPLAYED             INTERCEPTED
                 (write-ahead log)   (deterministic replay)  (modify behavior)
                       │                      │                      │
                       ▼                      ▼                      ▼
                  Time-travel           Reproduce any           Hot-patch
                   debugging              execution             production
```

Method calls, field access, and constructors are instrumented at build time, and become discrete, serializable messages at runtime based on your configuration. Because operations can become messages, they can be logged, intercepted, and replayed.

PAL is a post-compile weaving layer and runtime that adds message-passing semantics to Java operations. Method calls, field access, and constructors are instrumented at build time via AspectJ. At runtime, when logging, interception, or publishing is enabled, these instrumented operations become messages that can be logged to a write-ahead log, replayed for deterministic debugging, or intercepted by dynamic callbacks—without modifying your application source code.

The idea of reifying operations as messages has a long heritage: Smalltalk (1970s), Erlang/OTP, Akka, and others have built on this principle. PAL's contribution is applying it to existing Java code through bytecode weaving, enabling capabilities like write-ahead logging, deterministic replay, dynamic interception, and cross-peer invocation as runtime options rather than architectural commitments. All features are off by default—you enable only what you need, and pay no runtime cost for features you don't use.

This single abstraction enables capabilities that are not available in standard JVMs.

## One Abstraction, Many Capabilities

PAL's capabilities share a common foundation: operations represented as messages. Because method calls, field access, and constructors are instrumented, they can become messages that are logged, intercepted, and replayed—each unlocking a different set of capabilities:

**Logged:**

- Time-travel debugging (replay any execution)
- Audit trails (observe every operation)
- Event sourcing (automatic, transparent)

**Replayed:**

- Deterministic debugging (reproduce any execution exactly)
- Root cause analysis (step through the exact sequence that led to a failure)
- State reconstruction (rebuild state from the operation log)

**Intercepted:**

- Hot-patching production (modify behavior at runtime)
- Testing without mocks (replace implementations dynamically)
- Monitoring (observe calls as they happen)

Because intercept callbacks can mutate arguments, skip execution, or override return values, higher-level patterns like routing, filtering, transformation, and caching follow naturally from this mechanism.

Without modifying your application source code (AspectJ weaving is configured at build time). No annotations, no interfaces in your source files.

## What You Can Do

### Development & Testing

- Test real code without mocks (replay messages instead of stubbing dependencies)
- Debug with time-travel (replay any execution from the write-ahead log)
- Inspect method calls at runtime (observe message flow)

### Production

- Hot-patch bugs in 60 seconds (via runtime intercepts, as a targeted incident response measure until a proper fix is deployed)
- Audit every operation (messages provide compliance trail)
- Trace requests across distributed systems (messages carry context)

## Quick Start

### 1. Install PAL

```bash
# Clone and build
git clone https://github.com/quasientio/pal.git
cd pal
./mvnw install -DskipTests

# Add to PATH
export PATH="$(pwd)/bin:$PATH"

# Verify
pal help
```

See [Getting Started](getting-started.md) for detailed installation.

The distributed examples below assume the following environment variable:

```bash
export PAL_DIRECTORY=localhost:2379
```

and infrastructure containers started (see [Start/Stop Infrastructure](getting-started.md#startstop-infrastructure)).

### 2. Run Your First Peer

```bash
# Simple local peer with Chronicle Queue
pal run --wal file:/tmp/my-wal -cp myapp.jar com.example.Main

# Distributed peer with Kafka
pal run --wal my-wal -k localhost:29092 -cp myapp.jar com.example.Main
```

### 3. Call a Remote Method

```bash
# List running peers
pal peers

# Call a method
pal peer call peer-uuid com.example.Calculator add 5 3
```

## Core Concepts

### Peers

Peers are PAL runtime instances that execute your code. Each peer:

- Has a unique ID and optional name
- Can have its operations intercepted by dynamic callbacks
- Can register intercepts that target other peers' operations
- Can read from and write to [logs](concepts/peers-and-logs.md#what-is-a-log) (durable message streams backed by Kafka or Chronicle Queue)
- Can send and receive RPC calls
- Registers in a directory (etcd) for discovery

Learn more: [Peers and Logs](concepts/peers-and-logs.md)

### Remote Procedure Calls

PAL converts method calls into messages that can be:

- Sent to remote peers (RPC)
- Written to [logs](concepts/peers-and-logs.md#what-is-a-log) for replay
- Intercepted for testing/monitoring

Two formats: Binary (fast) and JSON (interoperable)

Learn more: [Remote Procedure Calls](concepts/rpc.md)

### Logs

Logs are durable message streams. PAL supports:

- **Chronicle Queue**: Memory-mapped files, ultra-fast, local
- **Kafka**: Distributed, networked, scalable

Choose Chronicle for local dev, Kafka for distributed systems.

Learn more: [Log Backends](concepts/logs.md)

### Interception

Insert callbacks before, after, or around any method call at runtime:

```java
// Register intercept via constructor
InterceptRequest<InterceptableMethodCall> intercept =
    new InterceptRequest<>(
        UUID.randomUUID(),           // intercept ID
        myPeerUuid,                  // callback peer
        InterceptType.BEFORE,        // type
        "com.example.Calculator",    // class to intercept
        "com.example.Monitor",       // callback class
        "onBeforeAdd",               // callback method
        new InterceptableMethodCall(
            "add", Arrays.asList("int", "int")));
palDirectory.createIntercept(intercept);

// Your callback gets called before Calculator.add()
```

Learn more: [Interception](concepts/interception.md)

## Common Use Cases

### Local Development and Testing

```bash
# Run with Chronicle (no infrastructure needed)
pal run --wal file:/tmp/dev-log -cp target/classes com.example.Dev

# Test with interception
# Register intercepts to verify method calls
```

Guide: [Local Development](guides/local-development.md)

### Building Distributed Applications

```bash
# Peer A: Service provider
pal run --wal service-wal --json-rpc 7767 -n my-service -cp service.jar com.example.Service

# Peer B: Client
pal peer call my-service com.example.Service processRequest data
```

Guide: [Distributed Application](guides/distributed-application.md)

### Testing with Interception

```bash
# Run application under test
pal run --interceptable -cp app.jar com.example.App

# Register intercepts from test code
# Verify method calls, inspect state
```

Guide: [Testing with Interception](guides/testing-with-interception.md)

## CLI Commands

| Command | Purpose |
|---------|---------|
| `pal init` | Initialize a project for PAL (scaffold or patch existing) |
| `pal run` | Start a peer and run application code |
| `pal peer` | Manage peers (ls, call, print, rm, stats) |
| `pal log` | Manage logs (ls, call, print, rm, index, stats) |
| `pal intercept` | Manage intercepts (ls) |
| `pal replay` | Replay application and verify against recorded WAL |

See [CLI Reference](cli-reference.md) for complete documentation.

## Example Workflows

### Capture and Replay Execution

```bash
# Run with WAL
pal run --wal file:/tmp/execution-log -cp app.jar com.example.Main arg1 arg2

# Print to see what happened
pal log print file:/tmp/execution-log --full

# Replay and verify execution against the WAL
pal replay --wal file:/tmp/execution-log -cp app.jar com.example.Main arg1 arg2
```

Guide: [Deterministic Replay](guides/deterministic-replay.md)

### Distributed RPC

```bash
# Terminal 1: Start service peer
pal run --wal service-wal --json-rpc 7767 -n calculator -cp calc.jar com.example.Calculator

# Terminal 2: Call service
pal peer call calculator com.example.Calculator add 10 20
```

### Monitor Method Calls

```bash
# Start peer with interception enabled
pal run --interceptable -n monitored-app -cp app.jar com.example.App

# From monitoring code, register intercepts
# Get callbacks for every method call
```

## Getting Help

- **Concepts**: Understand [peers](concepts/peers-and-logs.md), [RPC](concepts/rpc.md), [interception](concepts/interception.md), [logs](concepts/logs.md)
- **Guides**: Follow step-by-step [tutorials](guides/distributed-application.md)
- **CLI Reference**: Complete [command documentation](cli-reference.md)
- **Getting Started**: Detailed [installation guide](getting-started.md)

## Requirements

- **Java**: JDK 17 or later
- **Build**: Maven 3.x
- **Infrastructure** (for distributed mode):
    - etcd 3.x (directory service)
    - Kafka 3.x (distributed logs)
    - Docker (for local etcd/Kafka)

## Next Steps

1. **Install**: Follow [Getting Started](getting-started.md)
2. **Learn Concepts**: Read [Peers and Logs](concepts/peers-and-logs.md)
3. **Try It**: Follow [Local Development Guide](guides/local-development.md)
4. **Go Distributed**: Build a [Distributed Application](guides/distributed-application.md)

## License

PAL is licensed under the Apache License, Version 2.0.
