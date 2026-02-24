# Getting Started with PAL

This guide walks you through installing PAL, running your first peer, and experiencing how operations become messages.

## Prerequisites

Before you begin, ensure you have:

- **Java 17 or later** (check with `java -version`)
- **Maven 3.x** (check with `mvn -version`)
- **Git** (for cloning the repository)
- **Docker** (optional, for distributed mode with etcd/Kafka)

## Installation

### Option 1: Build from Source (Recommended)

```bash
# Clone the repository
git clone https://github.io/quasient/pal.git
cd pal

# Build and run tests (skips integration tests)
mvn install -DskipITs

# This produces:
# - Runtime libraries in modules/*/target/
# - Distribution in modules/distribution/target/
# - CLI tools in modules/pal-cli/target/
```

### Option 2: Download Binary Distribution

*Note: Binary distributions will be available once PAL reaches stable release.*

### Set Up Environment

Add PAL to your PATH:

```bash
# From the PAL source directory
export PAL_HOME="$(pwd)"
export PATH="$PAL_HOME/bin:$PATH"

# Verify installation
pal help
```

You should see:

```
Usage: pal [OPTIONS] COMMAND

The friendly java runtime

Commands:
  run    Run a new peer
  print  Print messages from peers or logs
  call   Send messages to peers or logs
  ls     List peers and logs in directory
  rm     Remove peers or logs from directory
```

## Your First PAL Application

Let's build a simple application and run it with PAL to see operations become messages.

### 1. Create a Simple Application

Create a directory for your project:

```bash
mkdir pal-tutorial
cd pal-tutorial
```

Create `src/main/java/tutorial/OrderService.java`:

```java
package tutorial;

public class OrderService {
    private int orderCount = 0;

    public void processOrder(String product, int quantity, double price) {
        orderCount++;
        double total = quantity * price;
        System.out.println("Processing order #" + orderCount);
        System.out.println("Product: " + product);
        System.out.println("Quantity: " + quantity);
        System.out.println("Total: $" + total);

        if (total > 100) {
            applyDiscount(total);
        }
    }

    private void applyDiscount(double total) {
        double discount = total * 0.1;
        System.out.println("Discount applied: $" + discount);
    }

    public int getOrderCount() {
        return orderCount;
    }
}
```

Create `src/main/java/tutorial/Main.java`:

```java
package tutorial;

public class Main {
    public static void main(String[] args) {
        OrderService service = new OrderService();

        System.out.println("=== Order Processing Demo ===\n");

        service.processOrder("Laptop", 1, 999.99);
        System.out.println();

        service.processOrder("Mouse", 2, 29.99);
        System.out.println();

        service.processOrder("Keyboard", 1, 149.99);
        System.out.println();

        System.out.println("Total orders processed: " + service.getOrderCount());
    }
}
```

### 2. Configure PAL with AspectJ

Create `pom.xml`:

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
        <pal.version>0.1.0-SNAPSHOT</pal.version>
        <aspectj.version>1.9.19</aspectj.version>
    </properties>

    <dependencies>
        <!-- PAL weave aspect library -->
        <dependency>
            <groupId>io.quasient.pal</groupId>
            <artifactId>pal-weave</artifactId>
            <version>${pal.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- AspectJ compiler plugin -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>aspectj-maven-plugin</artifactId>
                <version>1.14.0</version>
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

### 3. Build the Application

```bash
mvn clean compile

# Verify AspectJ weaving worked
javap -c target/classes/tutorial/OrderService.class | grep aspectOf

# You should see references to PAL aspects
```

### 4. Run with PAL (Local Mode)

Let's start simple: run with Chronicle Queue (no Kafka/etcd needed).

```bash
# Run the application with PAL
pal run --wal file:/tmp/tutorial-wal --json-rpc auto \
  -cp target/classes \
  tutorial.Main
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

### 5. Inspect the Messages

Let's see what PAL captured:

```bash
# Print all messages from the log
pal print -l file:/tmp/tutorial-wal --full
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

### 6. Replay the Execution

You can replay the execution from the log:

```bash
# Replay from the log (no Main.main() args needed)
pal run --source-log file:/tmp/tutorial-wal -cp target/classes
```

You'll see the same output as before, but this time it's being replayed from messages, not executed from Main.main().

**This enables time-travel debugging:** Any execution can be replayed exactly as it happened.

## Distributed Mode: Multiple Peers

Now let's see how PAL enables transparent RPC between peers.

### 1. Start Infrastructure

Start etcd and Kafka using Docker:

```bash
# From the PAL source directory
cd $PAL_HOME
infra/bin/start_etcd_and_kafka_docker.sh

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
pal call -d localhost:2379 -p calculator \
  tutorial.Calculator add 10 20

# Result: 30
```

**What happened:**

1. `pal call` created an ExecMessage: `{class: "Calculator", method: "add", args: [10, 20]}`
2. Looked up peer "calculator" in etcd directory
3. Sent message to calculator peer via ZeroMQ
4. Calculator peer received message, invoked `calc.add(10, 20)` via reflection
5. Returned result 30
6. Result sent back to caller

**Transparent RPC:** No stubs, no service definitions, no code generation.

### 5. List Peers and Logs

```bash
# List all registered peers
pal ls -d localhost:2379 -P -l

# Output:
# Peers:
#   abcd1234-... (calculator) - localhost:5555 - RUNNING

# List all logs
pal ls -d localhost:2379 -L -l

# Output:
# Logs:
#   calculator-wal - kafka - 45 messages
```

### 6. Inspect the RPC Messages

```bash
# Print messages from calculator's WAL
pal print -d localhost:2379 -l calculator-wal --full

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
# Start (from PAL_HOME)
infra/bin/start_etcd_and_kafka_docker.sh

# Stop
infra/bin/stop_etcd_and_kafka_docker.sh

# Check status
docker ps | grep -E "etcd|kafka"
```

### View Logs

```bash
# List all logs in directory
pal ls -d localhost:2379 -L

# Print messages from a log
pal print -d localhost:2379 -l my-log

# Print with full details
pal print -d localhost:2379 -l my-log --full

# Print specific message at offset 100
pal print -d localhost:2379 -l my-log -o 100
```

### Call Remote Methods

```bash
# By peer name
pal call -d localhost:2379 -p my-service \
  com.example.MyClass myMethod arg1 arg2

# By peer UUID
pal call -d localhost:2379 -p 550e8400-... \
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
pal rm -d localhost:2379 -P my-peer-uuid

# Remove a log from directory (doesn't delete Kafka topic)
pal rm -d localhost:2379 -L my-log

# Clean local Chronicle logs
rm -rf /tmp/tutorial-wal
```

## Troubleshooting

### "ERROR_UNREACHABLE_ETCD" (exit code 14)

```bash
# Check if etcd is running
curl http://localhost:2379/health

# If not, start it
cd $PAL_HOME
infra/bin/start_etcd_and_kafka_docker.sh
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
cd $PAL_HOME
infra/bin/start_etcd_and_kafka_docker.sh
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
# Verify weaving
javap -c target/classes/tutorial/OrderService.class | grep aspectOf

# If nothing found, check pom.xml has aspectj-maven-plugin
# And pal-weave in aspectLibraries
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
- ✓ How to compile applications with AspectJ weaving
- ✓ How operations become messages (quantization)
- ✓ How to run peers locally (Chronicle) and distributed (Kafka/etcd)
- ✓ How to inspect messages in logs
- ✓ How to call remote methods transparently
- ✓ How to replay executions from logs
- ✓ The basics of interception for dynamic behavior

PAL converts your operations into messages. Everything else flows from that simple transformation.
