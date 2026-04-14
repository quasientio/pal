![PAL Logo](assets/imgs/PAL-logo-01.png)

# Welcome to PAL

## The Core Insight

PAL converts every Java operation into a message.

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
                    LOGGED                 ROUTED              INTERCEPTED
                 (write-ahead log)    (remote peers)      (modify behavior)
                       │                      │                      │
                       ▼                      ▼                      ▼
                  Time-travel           Distributed              Hot-patch
                   debugging              systems               production
```

Method calls, field access, and constructors become discrete, serializable messages. Because operations are messages, they can be logged, routed, intercepted, and replayed.

This single abstraction enables capabilities that are impossible with standard JVMs.

## One Abstraction, Many Capabilities

PAL doesn't have separate features for testing, distributed systems, and production debugging. Instead, it provides one primitive: **operations as messages**. The capabilities emerge naturally:

**Because messages can be logged:**

- Time-travel debugging (replay any execution)
- Audit trails (observe every operation)
- Event sourcing (automatic, transparent)

**Because messages can be routed:**

- Remote procedure calls (transparent RPC without code changes)
- Distributed systems (objects communicate across network)
- Actor patterns (asynchronous messaging with normal objects)

**Because messages can be intercepted:**

- Hot-patching production (modify behavior at runtime)
- Testing without mocks (replace implementations dynamically)
- Monitoring (observe calls as they happen)

All without changing your code. No annotations, no interfaces, no framework lock-in.

## What You Can Do

### Development & Testing

- Test real code without mocks (replay messages instead of stubbing dependencies)
- Debug with time-travel (replay any execution from the write-ahead log)
- Inspect method calls at runtime (observe message flow)

### Production

- Hot-patch bugs in 60 seconds (intercept and replace behavior without restart)
- Audit every operation (messages provide compliance trail)
- Trace requests across distributed systems (messages carry context)

### Architecture

- Event-source your application automatically (every operation is logged)
- Implement actor patterns with normal objects (messages enable asynchronous communication)
- Route operations across network boundaries (transparent RPC)

## Quick Start

### 1. Install PAL

```bash
# Clone and build
git clone https://github.io/quasientinc/pal.git
cd pal
./mvnw install -DskipITs

# Add to PATH
export PATH="$(pwd)/bin:$PATH"

# Verify
pal help
```

See [Getting Started](getting-started.md) for detailed installation.

### 2. Run Your First Peer

```bash
# Simple local peer with Chronicle Queue
pal run --wal file:/tmp/my-wal --json-rpc auto \
  -cp myapp.jar com.example.Main

# Distributed peer with Kafka
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-wal --json-rpc auto \
  -cp myapp.jar com.example.Main
```

### 3. Call a Remote Method

```bash
# List running peers
pal peer ls -d localhost:2379

# Call a method
pal peer call -d localhost:2379 peer-uuid \
  com.example.Calculator add 5 3
```

## Core Concepts

### Peers

Peers are PAL runtime instances that execute your code. Each peer:

- Has a unique ID and optional name
- Can send and receive RPC calls
- Can read from and write to logs
- Registers in a directory (etcd) for discovery

Learn more: [Peers and Logs](concepts/peers-and-logs.md)

### Remote Procedure Calls

PAL converts method calls into messages that can be:

- Sent to remote peers (RPC)
- Written to logs for replay
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
pal run --wal file:/tmp/dev-log --json-rpc auto \
  -cp target/classes com.example.Dev

# Test with interception
# Register intercepts to verify method calls
```

Guide: [Local Development](guides/local-development.md)

### Building Distributed Applications

```bash
# Peer A: Service provider
pal run -d etcd:2379 -k kafka:9092 \
  --wal service-wal --json-rpc auto -n my-service \
  -cp service.jar com.example.Service

# Peer B: Client
pal peer call -d etcd:2379 my-service \
  com.example.Service processRequest data
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
| `pal run` | Start a peer and run application code |
| `pal peer` | Manage peers (ls, call, print, rm, stats) |
| `pal log` | Manage logs (ls, call, print, rm, index, stats) |
| `pal intercept` | Manage intercepts (ls) |
| `pal run` | Start a peer and run application code |
| `pal replay` | Replay application and verify against recorded WAL |

See [CLI Reference](cli-reference.md) for complete documentation.

## Example Workflows

### Capture and Replay Execution

```bash
# Run with WAL
pal run --wal file:/tmp/execution-log \
  -cp app.jar com.example.Main arg1 arg2

# Replay and verify execution against the WAL
pal replay --wal file:/tmp/execution-log \
  -cp app.jar com.example.Main arg1 arg2

# Or replay individual operations from the WAL
pal run --source-log file:/tmp/execution-log \
  -cp app.jar

# Print to see what happened
pal log print file:/tmp/execution-log --full
```

Guide: [Deterministic Replay](guides/deterministic-replay.md)

### Distributed RPC

```bash
# Terminal 1: Start service peer
pal run -d localhost:2379 -k localhost:29092 \
  --wal service-wal --json-rpc auto -n calculator \
  -cp calc.jar com.example.Calculator

# Terminal 2: Call service
pal peer call -d localhost:2379 calculator \
  com.example.Calculator add 10 20

# Result: 30
```

### Monitor Method Calls

```bash
# Start peer with interception enabled
pal run -d localhost:2379 --interceptable \
  --json-rpc auto -n monitored-app \
  -cp app.jar com.example.App

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
