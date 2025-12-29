# Building a Distributed Application

This guide walks you through building a distributed calculator service with PAL, demonstrating RPC, service discovery, and distributed logging.

## What We'll Build

A simple distributed system with:

- **Calculator Service**: Provides add/multiply operations
- **Client**: Calls the calculator remotely
- **Monitor**: Observes all operations via interception
- **Logging**: All operations logged to Kafka

## Prerequisites

- PAL installed and on PATH
- etcd running (directory service)
- Kafka running (distributed logs)

### Start Infrastructure

```bash
# In PAL directory
source export_env.sh
infra/bin/start_etcd_and_kafka_docker.sh

# Wait ~30 seconds for services to start

# Verify
curl http://localhost:2379/health  # etcd
docker ps | grep kafka  # Kafka
```

## Step 1: Create the Calculator Service

### CalculatorService.java

```java
package com.example.calculator;

public class CalculatorService {

    public static void main(String[] args) {
        System.out.println("Calculator Service started");

        // Keep running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Shutting down");
        }
    }

    public int add(int a, int b) {
        System.out.println("Adding " + a + " + " + b);
        return a + b;
    }

    public int multiply(int a, int b) {
        System.out.println("Multiplying " + a + " * " + b);
        return a * b;
    }
}
```

### Configure AspectJ Weaving

In `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.quasient.pal</groupId>
        <artifactId>pal-api</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>

<build>
    <plugins>
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
```

### Build

```bash
mvn clean install
```

## Step 2: Start the Calculator Service

```bash
pal run -d localhost:2379 -k localhost:29092 \
  --wal calculator-wal \
  --rpc auto \
  --json-rpc auto \
  --interceptable \
  -n calculator \
  -cp target/calculator-1.0-SNAPSHOT.jar \
  com.example.calculator.CalculatorService
```

**What this does**:

- `-d localhost:2379`: Registers in etcd directory
- `-k localhost:29092`: Uses Kafka for logs
- `--wal calculator-wal`: Writes all operations to Kafka topic
- `--rpc auto`: Enables binary RPC on random port
- `--json-rpc auto`: Enables JSON-RPC on random port
- `--interceptable`: Allows dynamic interception
- `-n calculator`: Service name for discovery

### Verify Service Started

```bash
$ pal ls -d localhost:2379 -P -l

UUID                                  Name        ZMQ-RPC             JSON-RPC            Uptime
550e8400-e29b-41d4-a716-446655440000  calculator  tcp://localhost:555 ws://localhost:9001 0:00:05
```

Service is running and ready for calls!

## Step 3: Call the Service

### From CLI

```bash
# Add 5 + 3
$ pal call -d localhost:2379 -p calculator \
    com.example.calculator.CalculatorService add 5 3

Result: 8

# Multiply 4 * 7
$ pal call -d localhost:2379 -p calculator \
    com.example.calculator.CalculatorService multiply 4 7

Result: 28
```

### Via JSON-RPC (for complex calls)

```bash
# Create calculator instance
echo '{"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.example.calculator.CalculatorService"}}' | \
  pal call -d localhost:2379 -p calculator

# Returns ObjectRef UUID: 660e9400-e39c-41e4-b726-556766550000

# Call instance method
echo '{"jsonrpc":"2.0","id":"2","method":"call","params":{"target":"660e9400-e39c-41e4-b726-556766550000","method":"add","args":[{"type":"int","value":10},{"type":"int","value":20}]}}' | \
  pal call -d localhost:2379 -p calculator

# Returns: 30
```

## Step 4: View the Operation Log

All operations are logged to Kafka:

```bash
# Print calculator's WAL
$ pal print -d localhost:2379 -l calculator-wal --output-format COMPACT

offset=0 id=abc123 message=CalculatorService.add(5, 3)
offset=1 id=abc124 message=CalculatorService.multiply(4, 7)
offset=2 id=abc125 message=CalculatorService.add(10, 20)

# Follow live (like tail -f)
$ pal print -d localhost:2379 -l calculator-wal -f

# Waiting for new messages...
```

## Step 5: Create a Client Application

### CalculatorClient.java

```java
package com.example.client;

import io.quasient.pal.cxn.PalDirectory;
import io.quasient.pal.core.PeerInfo;

public class CalculatorClient {

    public static void main(String[] args) {
        try {
            // Connect to directory
            PalDirectory directory = new PalDirectory("localhost:2379");

            // Find calculator service
            PeerInfo calcPeer = directory.findPeerByName("calculator");
            System.out.println("Found calculator at: " + calcPeer.getZmqRpcEndpoint());

            // Create RPC client
            ThinPeer client = new ThinPeer(calcPeer.getUuid(),
                                          calcPeer.getZmqRpcEndpoint());

            // Call add
            Object result = client.call(
                "com.example.calculator.CalculatorService",
                "add",
                new Object[]{15, 25}
            );
            System.out.println("15 + 25 = " + result);

            // Call multiply
            result = client.call(
                "com.example.calculator.CalculatorService",
                "multiply",
                new Object[]{6, 7}
            );
            System.out.println("6 * 7 = " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Run Client

```bash
mvn clean install

pal run -d localhost:2379 \
  -cp target/client-1.0-SNAPSHOT.jar \
  com.example.client.CalculatorClient

# Output:
# Found calculator at: tcp://localhost:5555
# 15 + 25 = 40
# 6 * 7 = 42
```

## Step 6: Add Monitoring

### MonitorService.java

```java
package com.example.monitor;

import io.quasient.pal.core.InterceptRequest;
import io.quasient.pal.core.InterceptType;
import io.quasient.pal.cxn.PalDirectory;

public class MonitorService {

    public static void main(String[] args) throws Exception {
        // Connect to directory
        PalDirectory directory = new PalDirectory("localhost:2379");

        // Start RPC server to receive callbacks
        UUID myUuid = UUID.randomUUID();

        // Register intercept for all calculator methods
        InterceptRequest intercept = InterceptRequest.builder()
            .classPattern("com.example.calculator.CalculatorService")
            .methodPattern("*")
            .interceptType(InterceptType.AFTER)
            .callbackPeer(myUuid)
            .build();

        directory.createIntercept(intercept);
        System.out.println("Monitoring all calculator operations...");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }

    // This method gets called automatically for each intercepted operation
    public void handleCallback(ExecMessage msg) {
        System.out.println("[MONITOR] " + msg.getMethod() +
                          " called with " + Arrays.toString(msg.getArgs()) +
                          " returned " + msg.getReturnValue());
    }
}
```

### Run Monitor

```bash
pal run -d localhost:2379 --rpc auto -n monitor \
  -cp target/monitor-1.0-SNAPSHOT.jar \
  com.example.monitor.MonitorService

# Output:
# Monitoring all calculator operations...

# When calculator is called:
# [MONITOR] add called with [15, 25] returned 40
# [MONITOR] multiply called with [6, 7] returned 42
```

## Step 7: Replay Operations

Stop the calculator service (Ctrl-C), then replay from the log:

```bash
pal run --source-log calculator-wal \
  -k localhost:29092 \
  -cp target/calculator-1.0-SNAPSHOT.jar \
  com.example.calculator.CalculatorService

# All previous operations are replayed
# Output:
# Adding 5 + 3
# Multiplying 4 + 7
# Adding 10 + 20
# Adding 15 + 25
# Multiplying 6 + 7
```

## Step 8: Scale Out

Start multiple calculator instances:

```bash
# Terminal 1
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal-1 --rpc auto -n calculator-1 \
  -cp target/calculator-1.0-SNAPSHOT.jar \
  com.example.calculator.CalculatorService

# Terminal 2
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal-2 --rpc auto -n calculator-2 \
  -cp target/calculator-1.0-SNAPSHOT.jar \
  com.example.calculator.CalculatorService

# Terminal 3
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal-3 --rpc auto -n calculator-3 \
  -cp target/calculator-1.0-SNAPSHOT.jar \
  com.example.calculator.CalculatorService
```

Now you have 3 calculator instances. Clients can call any of them:

```bash
# Call calculator-1
pal call -d localhost:2379 -p calculator-1 \
  com.example.calculator.CalculatorService add 1 2

# Call calculator-2
pal call -d localhost:2379 -p calculator-2 \
  com.example.calculator.CalculatorService add 3 4

# Call calculator-3
pal call -d localhost:2379 -p calculator-3 \
  com.example.calculator.CalculatorService add 5 6
```

## Architecture Diagram

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ (discovers via etcd)
       │
       ▼
┌─────────────────────────────────────────┐
│           etcd Directory                 │
│  - calculator: tcp://localhost:5555     │
│  - monitor: tcp://localhost:5556        │
│  - logs: [calculator-wal, ...]          │
└─────────────────────────────────────────┘
       │
       ├──────────────────┬─────────────┐
       │                  │             │
       ▼                  ▼             ▼
┌────────────────┐ ┌────────────┐ ┌──────────┐
│   Calculator   │ │  Monitor   │ │  Kafka   │
│    Service     │ │  Service   │ │  Topics  │
│                │ │            │ │          │
│  - add()       │ │ (intercepts│ │ - calc-  │
│  - multiply()  │ │  all calls)│ │   wal    │
└────────────────┘ └────────────┘ └──────────┘
```

## Cleanup

```bash
# Remove peers
pal rm -d localhost:2379 -P calculator monitor --force

# Remove logs
pal rm -d localhost:2379 -L calculator-wal

# Stop infrastructure
infra/bin/stop_etcd_and_kafka_docker.sh
```

## Key Takeaways

1. **Service Discovery**: etcd directory enables peers to find each other by name
2. **RPC**: Call remote methods transparently, PAL handles serialization/transport
3. **Logging**: All operations captured in Kafka for replay/audit
4. **Interception**: Monitor can observe operations without modifying calculator code
5. **Scaling**: Start multiple instances, each with its own log

## Next Steps

- [Testing with Interception](testing-with-interception.md) - Add automated tests
- [Local Development](local-development.md) - Develop without Kafka using Chronicle
- [Concepts: RPC](../concepts/rpc.md) - Deeper dive into RPC mechanisms
- [Concepts: Interception](../concepts/interception.md) - Advanced interception patterns
