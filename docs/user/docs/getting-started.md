# Getting Started with PAL

This guide walks you through installing PAL, running your first peer, and experiencing how operations become messages.

## Prerequisites

Before you begin, ensure you have:

- **Java 17 or later** (check with `java -version`)
- **Docker** (optional, for distributed mode with etcd/Kafka)

## Installation

### Option 1: Download Binary Distribution (Recommended)

Download the latest release from [GitHub Releases](https://github.com/quasientio/pal/releases/latest):

```bash
tar xzf pal-*.tar.gz
cd pal-*/

# Install to /usr/local (may need sudo)
./install.sh

# Or install to a custom directory
./install.sh --prefix=~/.local
```

This copies PAL to `PREFIX/lib/pal/` and creates a symlink at `PREFIX/bin/pal`. If `PREFIX/bin` is already on your PATH, you're ready to go.

### Option 2: Build from Source

Requires **Maven 3.x** and **Git**:

```bash
git clone https://github.com/quasientio/pal.git
cd pal
mvn install -DskipITs

export PATH="$(pwd)/bin:$PATH"
```

### Verify Installation

```bash
pal help
```

You should see:

```
Usage: pal [OPTIONS] COMMAND

The friendly java runtime

Management Commands:
  peer       Manage peers
  log        Manage logs
  intercept  Manage intercepts

Commands:
  run     Run a new peer
  replay  Deterministic WAL replay
  init    Initialize a project for PAL

Shortcuts:
  peers       List peers (shorthand for 'peer ls')
  logs        List logs (shorthand for 'log ls')
  intercepts  List intercepts (shorthand for 'intercept ls')

Run 'pal COMMAND --help' for more information on a command.
```

## Your First PAL Application

Let's build a simple application and run it with PAL to see operations become messages.

### 1. Create a Project with `pal init`

The fastest way to get started is with `pal init`, which sets up everything you need — build configuration, AspectJ weaving, sample code, and environment files:

```bash
pal init pal-tutorial
```

The interactive wizard guides you through the setup:

```
Welcome to PAL! Let's set up your project.

? Build tool:
  ❯ Gradle
    Maven

? Project group ID: [com.example]
? Project artifact ID: [pal-tutorial]
? Project version: [1.0-SNAPSHOT]
? Will this app be interceptable by other peers? [y/N]
? Will this app intercept other peers? [y/N]
? Main class (for pal run): [com.example.Main]
? Will you use Kafka for WAL (write-ahead log)? [y/N]

  ✓ Created build.gradle with AspectJ weaving
  ✓ Created settings.gradle
  ✓ Created src/main/java/com/example/Main.java
  ✓ Created src/main/java/com/example/SampleService.java
  ✓ Created config/peer-logging.xml
  ✓ Created config/cli-logging.xml
  ✓ Created .env.pal (environment variables)
  ✓ Created README.md

Next steps:
  1. cd pal-tutorial
  2. ./gradlew build
  3. pal run -cp build/classes/java/main com.example.Main
```

For scripted or CI environments, use non-interactive mode:

```bash
pal init pal-tutorial -y \
  --group-id com.example \
  --artifact-id pal-tutorial \
  --main-class com.example.Main
```

To enable all PAL features at once (interceptable, intercepting, JSON-RPC, Kafka, scope policy):

```bash
pal init pal-tutorial --all \
  --group-id com.example \
  --main-class com.example.Main
```

**Tip:** Use `--dry-run` to preview what `pal init` would generate without writing any files:

```bash
pal init pal-tutorial --dry-run
```

### 2. Build the Application

```bash
cd pal-tutorial
./gradlew build

# Verify AspectJ weaving worked
javap -c build/classes/java/main/com/example/SampleService.class | grep aspectOf

# You should see references to PAL aspects
```

### 3. Run with PAL (Local Mode)

Let's start simple: run with Chronicle Queue (no Kafka/etcd needed).

```bash
# Run the application with PAL
pal run --wal file:/tmp/tutorial-wal --json-rpc auto \
  -cp build/classes/java/main \
  com.example.Main
```

You should see the application output plus PAL startup messages:

```
[PAL] Peer starting: uuid=550e8400-...
[PAL] Chronicle WAL: /tmp/tutorial-wal
[PAL] RPC server: localhost:5555

=== Order Processing Demo ===

Processing order #1
Product: Laptop
Quantity: 1
Total: $999.99
Discount applied: $99.999

Processing order #2
Product: Mouse
Quantity: 2
Total: $59.98

Processing order #3
Product: Keyboard
Quantity: 1
Total: $149.99
Discount applied: $14.999

Total orders processed: 3

[PAL] Peer stopping
```

**What just happened?**

Every method call (`processOrder`, `applyDiscount`, `getOrderCount`) was converted to a message and logged to `/tmp/tutorial-wal`.

### 4. Inspect the Messages

Let's see what PAL captured:

```bash
# Print all messages from the log
pal log print file:/tmp/tutorial-wal --full
```

Output (abbreviated):

```
Message 0:
  Class: tutorial.Main
  Method: main
  Args: [[Ljava.lang.String;@1a2b3c]
  Timestamp: 2023-11-09T10:15:30.123Z

Message 1:
  Class: tutorial.OrderService
  Method: <init>
  Args: []
  Timestamp: 2023-11-09T10:15:30.125Z

Message 2:
  Class: tutorial.OrderService
  Method: processOrder
  Args: ["Laptop", 1, 999.99]
  Timestamp: 2023-11-09T10:15:30.130Z

Message 3:
  Class: tutorial.OrderService
  Method: applyDiscount
  Args: [999.99]
  Timestamp: 2023-11-09T10:15:30.132Z

Message 4:
  Class: tutorial.OrderService
  Method: processOrder
  Args: ["Mouse", 2, 29.99]
  Timestamp: 2023-11-09T10:15:30.135Z

...
```

**This is the key insight:** Every operation is now a discrete, inspectable message.

### 5. Replay the Execution

You can replay the execution from the log:

```bash
# Replay from the log (no Main.main() args needed)
pal run --source-log file:/tmp/tutorial-wal -cp build/classes/java/main
```

You'll see the same output as before, but this time it's being replayed from messages, not executed from Main.main().

**This enables time-travel debugging:** Any execution can be replayed exactly as it happened.

## Adding PAL to an Existing Project

If you already have a Maven or Gradle project, run `pal init` in the project directory. It detects the existing build file and patches it to add PAL weaving:

```bash
cd my-existing-project
pal init
```

```
Detected existing Maven project: com.acme:order-service (1.2.0)

? Will this app be interceptable by other peers? [y/N]
? Will this app intercept other peers? [y/N]
? Main class (for pal run): [com.acme.OrderServiceMain]
? Will you use Kafka for WAL (write-ahead log)? [y/N]

  ✓ [PATCH] pom.xml (added pal-weave dependency, aspectj-maven-plugin)
  ✓ Created config/peer-logging.xml
  ✓ Created config/cli-logging.xml
  ✓ Created .env.pal
  ✓ Created README.md
```

A backup of your original build file is created automatically (`pom.xml.backup` or `build.gradle.backup`). Use `--dry-run` to preview the changes before applying them:

```bash
pal init --dry-run
```

For Gradle projects, the same flow applies — `pal init` auto-detects `build.gradle`:

```bash
cd my-gradle-project
pal init
```

## Manual Setup (Alternative)

If you prefer full control over the build configuration, you can set up PAL manually instead of using `pal init`.

Create a `pom.xml` with the AspectJ weaving plugin and `pal-weave` dependency:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>pal-tutorial</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <pal.version>1.0.0-SNAPSHOT</pal.version>
        <aspectj.version>1.9.24</aspectj.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.quasient.pal</groupId>
            <artifactId>pal-weave</artifactId>
            <version>${pal.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>aspectj-maven-plugin</artifactId>
                <version>1.15.0</version>
                <configuration>
                    <complianceLevel>17</complianceLevel>
                    <source>17</source>
                    <target>17</target>
                    <aspectLibraries>
                        <aspectLibrary>
                            <groupId>io.quasient.pal</groupId>
                            <artifactId>pal-weave</artifactId>
                        </aspectLibrary>
                    </aspectLibraries>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

For Gradle, add the AspectJ post-compile weaving plugin and PAL dependency to your `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.freefair.aspectj.post-compile-weaving' version '8.6'
}

dependencies {
    aspect 'io.quasient.pal:pal-weave:1.0.0-SNAPSHOT'
    implementation 'org.aspectj:aspectjrt:1.9.24'
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

Then create your Java source files, build with `mvn compile` (or `gradle build`), and run with `pal run` as described above.

## Distributed Mode: Multiple Peers

Now let's see how PAL enables transparent RPC between peers.

### 1. Start Infrastructure

Start etcd and Kafka using Docker. If you used `pal init` with `--interceptable`, `--intercepting`, or `--kafka`, your project already has an `infra/` directory with Docker Compose files. Otherwise, use the ones bundled with PAL:

```bash
# From your project's infra/ directory (if generated by pal init)
infra/start.sh

# Or from the PAL installation directory
cd /usr/local/lib/pal/infra && ./start.sh

# Verify they're running
curl http://localhost:2379/health
# Should return: {"health":"true"}
```

### 2. Create a Service Peer

Create `src/main/java/tutorial/Calculator.java`:

```java
package tutorial;

public class Calculator {
    public int add(int a, int b) {
        System.out.println("Calculator.add(" + a + ", " + b + ")");
        return a + b;
    }

    public int multiply(int a, int b) {
        System.out.println("Calculator.multiply(" + a + ", " + b + ")");
        return a * b;
    }
}
```

Create `src/main/java/tutorial/CalculatorService.java`:

```java
package tutorial;

public class CalculatorService {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println("Calculator service ready");

        // Keep running to handle RPC calls
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Service stopping");
        }
    }
}
```

Rebuild:

```bash
mvn compile
```

### 3. Run the Service Peer

In one terminal:

```bash
pal run -d localhost:2379 -k localhost:29092 \
  --wal calculator-wal --json-rpc auto -n calculator \
  -cp target/classes \
  tutorial.CalculatorService
```

You should see:

```
[PAL] Peer starting: uuid=abcd1234-..., name=calculator
[PAL] Registered in directory: localhost:2379
[PAL] Kafka WAL: calculator-wal
[PAL] RPC server (Binary): localhost:5555
[PAL] RPC server (JSON): ws://localhost:8080/rpc
Calculator service ready
[PAL] Ready to serve requests
```

### 4. Call the Service Remotely

In another terminal:

```bash
# Call the add method
pal peer call -d localhost:2379 calculator \
  tutorial.Calculator add 10 20

# Result: 30
```

**What happened:**

1. `pal peer call` created an ExecMessage: `{class: "Calculator", method: "add", args: [10, 20]}`
2. Looked up peer "calculator" in etcd directory
3. Sent message to calculator peer via ZeroMQ
4. Calculator peer received message, invoked `calc.add(10, 20)` via reflection
5. Returned result 30
6. Result sent back to caller

**Transparent RPC:** No stubs, no service definitions, no code generation.

### 5. List Peers and Logs

```bash
# List all registered peers
pal peer ls -d localhost:2379 -l

# Output:
# Peers:
#   abcd1234-... (calculator) - localhost:5555 - RUNNING

# List all logs
pal log ls -d localhost:2379 -l

# Output:
# Logs:
#   calculator-wal - kafka - 45 messages
```

### 6. Inspect the RPC Messages

```bash
# Print messages from calculator's WAL
pal log print -d localhost:2379 calculator-wal --full

# You'll see the RPC call captured:
# Message 0:
#   Class: tutorial.Calculator
#   Method: add
#   Args: [10, 20]
#   Result: 30
#   Peer: calculator (abcd1234-...)
```

## Interception: Dynamic Behavior Modification

Let's see how to intercept method calls at runtime.

### 1. Create a Monitoring Peer

Create `src/main/java/tutorial/Monitor.java`:

```java
package tutorial;

public class Monitor {
    // This method will be called when Calculator methods are intercepted
    public void onCalculatorCall(String method, Object[] args) {
        System.out.println("[MONITOR] Intercepted: " + method);
        System.out.println("[MONITOR] Args: " + java.util.Arrays.toString(args));
        System.out.println("[MONITOR] Timestamp: " + System.currentTimeMillis());
        System.out.println();
    }
}
```

Create `src/main/java/tutorial/MonitorService.java`:

```java
package tutorial;

import io.quasient.pal.cxn.PalDirectory;
import io.quasient.pal.core.api.InterceptRequest;
import io.quasient.pal.core.api.InterceptType;

public class MonitorService {
    public static void main(String[] args) throws Exception {
        Monitor monitor = new Monitor();
        System.out.println("Monitor service starting");

        // Connect to PAL directory
        PalDirectory directory = ...; // Get from runtime

        // Register intercept for all Calculator methods
        UUID myPeerUuid = UUID.fromString(System.getProperty("pal.peer.uuid"));
        InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
            "tutorial.Calculator",
            "tutorial.Monitor",
            "onCalculatorCall",
            new InterceptableMethodCall("*", Collections.emptyList()));

        directory.createIntercept(intercept);

        System.out.println("Monitoring Calculator methods");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

### 2. Run with Interception

*Note: Full interception example requires additional API setup. See [Testing with Interception](guides/testing-with-interception.md) for complete code.*

The key point: You can register intercepts at runtime that execute callbacks before, after, or around any method call.

## Common Operations

### Start/Stop Infrastructure

```bash
# Start infrastructure (from your project's infra/ directory)
infra/start.sh

# Stop
infra/stop.sh

# Check status
docker ps | grep -E "etcd|kafka"
```

### View Logs

```bash
# List all logs in directory
pal log ls -d localhost:2379

# Print messages from a log
pal log print -d localhost:2379 my-log

# Print with full details
pal log print -d localhost:2379 my-log --full

# Print specific message at offset 100
pal log print -d localhost:2379 my-log -o 100
```

### Call Remote Methods

```bash
# By peer name
pal peer call -d localhost:2379 my-service \
  com.example.MyClass myMethod arg1 arg2

# By peer UUID
pal peer call -d localhost:2379 550e8400-... \
  com.example.MyClass myMethod arg1 arg2

# With JSON-RPC (for non-Java clients)
curl -X POST http://localhost:8080/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "MyClass.myMethod",
    "params": ["arg1", "arg2"],
    "id": 1
  }'
```

### Clean Up

```bash
# Remove a peer from directory
pal peer rm -d localhost:2379 my-peer-uuid

# Remove a log from directory (doesn't delete Kafka topic)
pal log rm -d localhost:2379 my-log

# Clean local Chronicle logs
rm -rf /tmp/tutorial-wal
```

## Troubleshooting

### "ERROR_UNREACHABLE_ETCD" (exit code 14)

```bash
# Check if etcd is running
curl http://localhost:2379/health

# If not, start it
infra/bin/start-etcd-and-kafka-docker.sh
```

### "ERROR_NO_KAFKA_SERVERS_GIVEN" (exit code 6)

```bash
# Ensure --kafka-servers is provided when using Kafka logs
pal run -k localhost:29092 --wal my-log -cp app.jar
```

### "ERROR_INITIALIZING_LOGS" (exit code 7)

Common causes:

**Kafka unreachable:**
```bash
# Check if Kafka is running
docker ps | grep kafka

# If not, start infrastructure
infra/bin/start-etcd-and-kafka-docker.sh
```

**Chronicle source log doesn't exist:**
```bash
# Ensure the log exists
ls -la /tmp/tutorial-wal

# If not, create it by running a peer with --wal first
pal run --wal file:/tmp/tutorial-wal -cp app.jar Main
```

### AspectJ Weaving Not Working

```bash
# Verify weaving (adjust path for your build tool)
javap -c build/classes/java/main/tutorial/OrderService.class | grep aspectOf

# If nothing found, check your build file includes pal-weave
# and the AspectJ weaving configuration
```

### Interception Not Working

1. Ensure class was compiled with AspectJ weaving
2. Ensure peer started with `--interceptable` flag
3. Check intercept pattern matches class/method names
4. Enable debug logging in logback.xml:

```xml
<logger name="io.quasient.pal.core.InterceptMatcher" level="DEBUG"/>
```

## Next Steps

Now that you've experienced PAL's core capabilities:

1. **Understand the concepts:** Read [Understanding PAL](understanding-pal.md) for deep dive
2. **Explore use cases:** See [Use Cases](use-cases.md) for your role (developer, SRE, architect)
3. **Learn the details:**
   - [Peers and Logs](concepts/peers-and-logs.md)
   - [RPC](concepts/rpc.md)
   - [Interception](concepts/interception.md)
   - [Log Backends](concepts/logs.md)
4. **Follow guides:**
   - [Local Development](guides/local-development.md)
   - [Distributed Application](guides/distributed-application.md)
   - [Testing with Interception](guides/testing-with-interception.md)

## Summary

You've learned:

- ✓ How to install and set up PAL
- ✓ How to scaffold a project with `pal init` (or configure manually)
- ✓ How to add PAL to an existing project with `pal init`
- ✓ How to compile applications with AspectJ weaving
- ✓ How operations become messages (quantization)
- ✓ How to run peers locally (Chronicle) and distributed (Kafka/etcd)
- ✓ How to inspect messages in logs
- ✓ How to call remote methods transparently
- ✓ How to replay executions from logs
- ✓ The basics of interception for dynamic behavior

PAL converts your operations into messages. Everything else flows from that simple transformation.

## Need Help?

Join us on [Discord](https://discord.gg/k4XsMyUA) — ask questions, share feedback, or just say hello.
