<p>
<img src="site/imgs/PAL-logo-01.png" alt="pal logo">
<br>
<h1>PAL - Message-Passing Runtime for Java</h1>
</p>

## Every Operation Is Instrumented

PAL instruments every Java operation at build time. At runtime, instrumented operations become messages when you enable logging, interception, or publishing—and only for operations within your configured recording scope.

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

This single transformation enables capabilities that are not available in standard JVMs. All features are off by default—you enable only what you need, and unused features carry minimal overhead. The runtime is lightweight: ~42 MB JAR, ~160 ms launch-to-ready for a detached peer with no infrastructure (AMD Ryzen 5 7600, JDK 17).

## What You Can Do

**Development & Testing:**
- Test real code without mocks (intercept dependencies at runtime instead of stubbing)
- Debug with time-travel (replay any execution from the write-ahead log)
- Inspect method calls at runtime (observe message flow)

**Production:**
- Hot-patch behavior at runtime (intercept and replace operations without restart)
- Audit every operation (messages provide compliance trail)

**Architecture:**
- Event-source your application automatically (for operations within recording scope)
- Invoke methods on remote peers (cross-peer RPC via ZMQ or JSON-RPC)

Without modifying your application source code (AspectJ weaving is configured at build time — see [Getting Started](https://quasientio.github.io/pal/getting-started/)). No annotations, no interfaces in your source files.

## How It Works

```
┌─────────────────────────────────────────────────────┐
│  Your Java Code (unchanged)                         │
│  calculator.add(2, 3)                               │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  PAL (AspectJ instrumentation at build time)        │
│  Dispatch: operation → ExecMessage (when enabled)   │
└─────────────────┬───────────────────────────────────┘
                  │
        ┌─────────┴─────────┬─────────────┐
        ▼                   ▼             ▼
  ┌──────────┐        ┌──────────┐  ┌──────────┐
  │   LOG    │        │  REPLAY  │  │ INTERCEPT│
  │  (WAL)   │        │ (re-run) │  │ (modify) │
  └──────────┘        └──────────┘  └──────────┘
       │                   │              │
       ▼                   ▼              ▼
  Audit/Debug       Deterministic    Hot-patch
                       replay        Production
```

Because operations are instrumented, they can be:
- **Logged** → Write-ahead log for replay and debugging
- **Replayed** → Deterministic reproduction of any execution
- **Intercepted** → Dynamic behavior modification at runtime
- **Inspected** → Real-time observability of execution

## Quick Start

### Prerequisites

- Java 17 or higher
- Docker (optional; for running etcd and Kafka)

### Option 1: Download Binary Distribution

Download the latest release from [GitHub Releases](https://github.com/quasientio/pal/releases/latest), then:

```bash
tar xzf pal-*.tar.gz
cd pal-*/

# Install to /usr/local (may need sudo)
./install.sh

# Or install to a custom directory
./install.sh --prefix=~/.local
```

Verify the installation:

```bash
pal help
```

### Option 2: Build from Source

```bash
# Clone and build (requires Maven 3)
git clone https://github.com/quasientio/pal.git
cd pal
./mvnw install -DskipITs

# Add to PATH
export PATH="$(pwd)/bin:$PATH"
```

### Scaffold a Project

`pal init` generates a Maven project pre-wired with AspectJ weaving and the right dependencies:

```bash
pal init my-app
cd my-app
./gradlew build
```

Answer **y** to the Kafka prompt (or pass `--all`) to also generate `infra/start.sh` and `infra/stop.sh` that bring up etcd and Kafka via Docker.

### Run Your First Peer

Local mode (Chronicle, no infrastructure needed):

```bash
pal run --wal file:/tmp/my-wal -cp build/classes/java/main com.example.MyApp
```

Distributed mode (etcd + Kafka):

```bash
infra/start.sh
pal run -d localhost:2379 -k localhost:29092 \
  --wal my-wal --json-rpc auto -n my-service \
  -cp build/classes/java/main com.example.MyApp
```

## Documentation

**User Documentation:** [quasientio.github.io/pal](https://quasientio.github.io/pal/)
- [Understanding PAL](https://quasientio.github.io/pal/understanding-pal/) - Deep dive on the message abstraction
- [Getting Started](https://quasientio.github.io/pal/getting-started/) - Hands-on tutorial
- [Use Cases](https://quasientio.github.io/pal/use-cases/) - Scenarios for developers, SREs, and architects
- [CLI Reference](https://quasientio.github.io/pal/cli-reference/) - Complete command reference

**API Javadoc:** [javadoc.io/doc/io.quasient.pal](https://javadoc.io/doc/io.quasient.pal)

## Core Concepts

**Peers:** Processes that send and receive messages. Each peer has a unique UUID and can be addressed by name.

**Logs:** Durable, ordered streams of messages. Backed by Kafka (distributed) or Chronicle Queue (local, high-performance).

**Interception:** Dynamic registration of callbacks that execute before, after, or around operations. Enables hot-patching production code.

**RPC:** Methods on remote peers can be invoked programmatically, supporting intercept callbacks, development workflows, and operational tooling.

## One Abstraction, Many Capabilities

PAL doesn't have separate features for testing, production debugging, and event sourcing. Instead, it provides one primitive: **operations as messages**.

Because operations are messages, they can be logged (enabling time-travel debugging), intercepted (enabling hot-patching and patterns like routing, filtering, and caching), and replayed (enabling deterministic debugging). These capabilities follow from the abstraction.

### Prior Art & Motivation

The idea of reifying operations as messages has deep roots: Smalltalk, Erlang/OTP, Akka, and others. PAL applies this principle to existing Java code through build-time bytecode weaving, enabling message-passing capabilities as runtime options rather than architectural commitments.

## License

Apache License, Version 2.0.

See [LICENSE](LICENSE) for details.

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Bug reports and feature requests: [GitHub Issues](https://github.com/quasientio/pal/issues)
- Code contributions: [GitHub Pull Requests](https://github.com/quasientio/pal/pulls)

## Community

- **Discord:** [Join the PAL community](https://discord.gg/cHrbfsB2ev)
- **Documentation:** [Full docs](https://quasientio.github.io/pal/)
- **Examples:** [pal-examples repository](https://github.com/quasientio/pal-examples)
- **Contact:** [manuel@quasient.com](mailto:manuel@quasient.com)
