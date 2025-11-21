<p>
<img src="site/imgs/PAL-logo-01.png" alt="pal logo">
<br>
<h1>PAL - Message-Passing Runtime for Java</h1>
</p>

## Every Operation Becomes a Message

PAL converts Java operations into messages. Method calls, field access, and constructors become discrete, serializable messages that can be logged, routed, intercepted, and replayed.

```java
// Your code
calculator.add(2, 3);

// What PAL sees
ExecMessage msg = {
  class: "Calculator",
  method: "add",
  args: [2, 3],
  timestamp: 1699564800000
};
```

This single transformation enables capabilities that are impossible with standard JVMs.

## What You Can Do

**Development & Testing:**
- Test real code without mocks (replay messages instead of stubbing dependencies)
- Debug with time-travel (replay any execution from the write-ahead log)
- Inspect method calls at runtime (observe message flow)

**Production:**
- Hot-patch bugs in 60 seconds (intercept and replace behavior without restart)
- Audit every operation (messages provide compliance trail)
- Trace requests across distributed systems (messages carry context)

**Architecture:**
- Event-source your application automatically (every operation is logged)
- Implement actor patterns with normal objects (messages enable asynchronous communication)
- Route operations across network boundaries (transparent RPC)

All without changing your code. No annotations, no interfaces, no framework lock-in.

## How It Works

```
┌─────────────────────────────────────────────────────┐
│  Your Java Code (unchanged)                         │
│  calculator.add(2, 3)                               │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  PAL Runtime (AspectJ weaving at build time)        │
│  Converts: operation → ExecMessage                  │
└─────────────────┬───────────────────────────────────┘
                  │
        ┌─────────┴─────────┬─────────────┐
        ▼                   ▼             ▼
  ┌──────────┐        ┌──────────┐  ┌──────────┐
  │   LOG    │        │   ROUTE  │  │ INTERCEPT│
  │  (WAL)   │        │  (RPC)   │  │ (modify) │
  └──────────┘        └──────────┘  └──────────┘
       │                   │              │
       ▼                   ▼              ▼
  Replay/Debug      Distributed      Hot-patch
                      Systems        Production
```

Because operations are messages, they can be:
- **Logged** → Write-ahead log for replay and debugging
- **Routed** → RPC across peers without code changes
- **Intercepted** → Dynamic behavior modification at runtime
- **Inspected** → Real-time observability of execution

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3
- Docker (for etcd and Kafka, or use Chronicle for local-only development)

### Build from Source

```bash
# Clone and build
git clone https://github.com/quasient/pal.git
cd pal
mvn install -DskipITs

# Set up environment
export PAL_HOME="$(pwd)"
export PATH="$PAL_HOME/bin:$PATH"
```

### Run Your First PAL Application

```bash
# Start infrastructure (etcd + Kafka in Docker)
infra/bin/start_etcd_and_kafka_docker.sh

# Run a simple peer
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-log --rpc auto \
  -cp target/my-app.jar \
  com.example.Main

# Or use Chronicle for local development (no Kafka needed)
pal run --wal file:/tmp/my-wal --rpc auto \
  -cp target/my-app.jar \
  com.example.Main
```

See the [Getting Started Guide](docs/user/docs/getting-started.md) for a complete tutorial.

## Documentation

**User Documentation:** [docs/user/docs/](docs/user/docs/)
- [Understanding PAL](docs/user/docs/understanding-pal.md) - Deep dive on the message abstraction
- [Getting Started](docs/user/docs/getting-started.md) - Hands-on tutorial
- [Use Cases](docs/user/docs/use-cases.md) - Scenarios for developers, SREs, and architects
- [CLI Reference](docs/user/docs/cli-reference.md) - Complete command reference

**Developer Documentation:** [docs/developer/docs/](docs/developer/docs/)
- [Architecture Overview](docs/developer/docs/architecture/overview.md)
- [Building and Testing](docs/developer/docs/development/building.md)
- [Code Conventions](docs/developer/docs/development/code-conventions.md)

## Core Concepts

**Peers:** Processes that send and receive messages. Each peer has a unique UUID and can be addressed by name.

**Logs:** Durable, ordered streams of messages. Backed by Kafka (distributed) or Chronicle Queue (local, high-performance).

**Quantization:** The process of converting operations (method calls, field access, constructors) into discrete messages.

**Interception:** Dynamic registration of callbacks that execute before, after, or around operations. Enables hot-patching production code.

**RPC:** Transparent remote procedure calls. Objects on remote peers are invoked as if local.

## One Abstraction, Many Capabilities

PAL doesn't have separate features for testing, production debugging, and event sourcing. Instead, it provides one primitive: **operations as messages**.

Because operations are messages, they can be logged (enabling time-travel debugging), routed (enabling distributed systems), and intercepted (enabling hot-patching). These capabilities emerge naturally from the abstraction.

## Architecture

PAL consists of:
- **pal-weave:** AspectJ aspects that intercept operations at build time
- **pal-runtime:** Message creation, routing, logging, and interception at runtime
- **pal-api:** Core types, interfaces, common utilities, and message serialization (Colfer/JSON-RPC)
- **pal-client:** Directory service (etcd), RPC clients with fluent DSL, and peer management
- **pal-cli:** Command-line interface for running peers and inspecting logs

## License

Business Source License 1.1 (converts to Apache 2.0 on 2029-10-01).

See [LICENSE](LICENSE) for details.

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Bug reports and feature requests: [GitHub Issues](https://github.com/quasient/pal/issues)
- Code contributions: [GitHub Pull Requests](https://github.com/quasient/pal/pulls)

## Community

- **Documentation:** [Full docs](docs/user/docs/)
- **Examples:** [pal-examples repository](https://github.com/cometera/pal-examples)
- **Discussion:** [GitHub Discussions](https://github.com/quasient/pal/discussions)
