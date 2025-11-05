# Local Development Guide

This guide shows you how to develop PAL applications locally without setting up etcd or Kafka. Using Chronicle Queue, you can build, test, and debug quickly on a single machine.

## Why Develop Locally?

**Benefits of local development**:

- **Zero infrastructure**: No etcd or Kafka cluster needed
- **Fast iteration**: Compile and run immediately
- **Easy debugging**: Standard IDE debugging works
- **Low latency**: Chronicle Queue is ultra-fast (nanoseconds)
- **Simple testing**: No network configuration required

**When you need it**:

- Building a new feature
- Writing unit/integration tests
- Debugging application logic
- Performance benchmarking
- Learning PAL

## Prerequisites

**Required**:

- JDK 17 or later (set `JAVA_HOME`)
- Maven 3.x
- PAL installed and on PATH

**Not required**:

- Docker
- etcd
- Kafka
- Network configuration

## Quick Setup

### 1. Verify PAL Installation

```bash
$ pal help

PAL (Peers And Logs) - Message-passing runtime for Java
Usage: pal [COMMAND] [OPTIONS]
...
```

If `pal` command not found, add PAL to your PATH:

```bash
export PAL_HOME="/path/to/pal"
export PATH="$PAL_HOME/bin:$PATH"
```

### 2. Create a Simple Application

**HelloService.java**:

```java
package com.example;

public class HelloService {
    public static void main(String[] args) {
        System.out.println("Hello Service started");

        for (String arg : args) {
            processMessage(arg);
        }
    }

    public static void processMessage(String msg) {
        System.out.println("Processing: " + msg);
        String result = msg.toUpperCase();
        System.out.println("Result: " + result);
    }
}
```

### 3. Configure AspectJ Weaving

**pom.xml**:

```xml
<dependencies>
    <dependency>
        <groupId>com.quasient.pal</groupId>
        <artifactId>pal-core-api</artifactId>
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
                <aspectLibraries>
                    <aspectLibrary>
                        <groupId>com.quasient.pal</groupId>
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

### 4. Build

```bash
mvn clean install
```

Your application is now ready to run with PAL!

## Basic Development Workflow

### Run with Chronicle Queue

```bash
pal run --wal file:/tmp/dev-wal \
  -cp target/myapp-1.0-SNAPSHOT.jar \
  com.example.HelloService hello world
```

**Output**:
```
Hello Service started
Processing: hello
Result: HELLO
Processing: world
Result: WORLD
```

### Inspect the WAL

```bash
pal print -l file:/tmp/dev-wal --output-format COMPACT
```

See all operations logged:

```
offset=0 id=abc123 message=HelloService.processMessage("hello")
offset=1 id=abc124 message=HelloService.processMessage("world")
```

### Replay from WAL

Stop the peer (Ctrl-C), then replay:

```bash
pal run --source-log file:/tmp/dev-wal \
  -cp target/myapp-1.0-SNAPSHOT.jar \
  com.example.HelloService
```

All logged operations are re-executed.

## Fast Iteration Loop

### 1. Edit Code

Make changes to your Java source:

```java
public static void processMessage(String msg) {
    System.out.println("Processing: " + msg);
    String result = msg.toUpperCase() + "!";  // Added !
    System.out.println("Result: " + result);
}
```

### 2. Rebuild

```bash
mvn clean compile
```

AspectJ weaving happens automatically.

### 3. Run Immediately

```bash
pal run --wal file:/tmp/test-wal \
  -cp target/classes \
  com.example.HelloService test
```

**Tip**: Use `target/classes` instead of JAR for fastest iteration.

### 4. Verify

Check output, inspect logs, repeat.

## Local Testing with Interception

### Setup Test Environment

No etcd or Kafka needed - just Chronicle queues:

```java
public class HelloServiceTest {

    private Path testWalPath;
    private List<ExecMessage> callbacks;

    @Before
    public void setUp() throws Exception {
        // Create temporary Chronicle queue
        testWalPath = Files.createTempDirectory("test-wal");
        callbacks = new CopyOnWriteArrayList<>();
    }

    @Test
    public void testProcessMessageCalled() throws Exception {
        // Start application peer
        ProcessBuilder pb = new ProcessBuilder(
            "pal", "run",
            "--wal", "file:" + testWalPath,
            "--interceptable",
            "-cp", "target/classes",
            "com.example.HelloService", "test-input"
        );
        Process peer = pb.start();

        // Wait for execution
        Thread.sleep(1000);

        // Read WAL to verify
        List<String> logOutput = readChronicleLog(testWalPath);
        assertTrue(logOutput.stream()
            .anyMatch(msg -> msg.contains("processMessage")));

        peer.destroy();
    }

    @After
    public void tearDown() throws IOException {
        // Cleanup
        deleteDirectory(testWalPath);
    }
}
```

### Intercept Without Directory

For local testing, you can use in-process interception:

```java
// Register intercept before calling method
InterceptMatcher matcher = new InterceptMatcher();
matcher.registerIntercept(
    "com.example.HelloService",
    "processMessage",
    InterceptType.BEFORE,
    (msg) -> {
        System.out.println("Intercepted: " + msg.getMethod());
        callbacks.add(msg);
    }
);

// Execute
HelloService.processMessage("test");

// Verify
assertEquals(1, callbacks.size());
```

## Debugging Locally

### Using IDE Debugger

Run PAL peer from your IDE for breakpoint debugging:

**IntelliJ Run Configuration**:

- **Main class**: `com.quasient.pal.cli.Pal`
- **Program arguments**: `run --wal file:/tmp/debug-wal -cp target/classes com.example.HelloService arg1`
- **VM options**: `-DPAL_HOME=/path/to/pal`

Set breakpoints in your application code and debug normally.

### Enable Debug Logging

Create local logging config:

**.local/conf/peer-logging.xml**:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Debug your package -->
    <logger name="com.example" level="DEBUG"/>

    <!-- Debug PAL internals if needed -->
    <logger name="com.quasient.pal.core.dispatcher" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

Run with custom logging:

```bash
export PAL_PEER_LOGGING_CONFIG=".local/conf/peer-logging.xml"
pal run --wal file:/tmp/debug-wal -cp target/classes com.example.HelloService
```

### Print All Messages

Watch messages in real-time:

```bash
# Terminal 1: Run peer
pal run --wal file:/tmp/live-wal -cp target/classes com.example.HelloService

# Terminal 2: Follow log
pal print -l file:/tmp/live-wal -f --output-format FULL
```

See every method call, argument, and return value.

## Performance Testing Locally

Chronicle Queue's low latency makes it ideal for benchmarking:

```bash
# Warm up
for i in {1..1000}; do
  pal run --wal file:/tmp/warmup \
    -cp target/classes com.example.HelloService test
done

# Benchmark
time for i in {1..10000}; do
  pal run --wal file:/tmp/bench \
    -cp target/classes com.example.HelloService test
done
```

Measure pure application performance without network overhead.

## Tips for Fast Development

### 1. Use Relative Paths

```bash
# Easier than absolute paths
pal run --wal file:dev-wal -cp target/classes com.example.Main
```

Chronicle queue created in current directory.

### 2. Reuse WAL for Replay

```bash
# Run once
pal run --wal file:session1 -cp target/classes com.example.Main input1

# Replay multiple times while debugging
pal run --source-log file:session1 -cp target/classes com.example.Main
```

No need to re-create test data.

### 3. Clean WAL Between Tests

```bash
# Start fresh
rm -rf /tmp/test-wal
pal run --wal file:/tmp/test-wal -cp target/classes com.example.Main
```

### 4. Use target/classes for Speed

```bash
# Fast (no JAR packaging)
mvn compile
pal run --wal file:dev -cp target/classes com.example.Main

# Slower
mvn package
pal run --wal file:dev -cp target/app.jar com.example.Main
```

### 5. Incremental Compilation

```bash
# Only recompile changed files
mvn compile

# Full clean when needed
mvn clean compile
```

## When to Switch to Distributed

**Stick with Chronicle locally when**:

- Developing single-peer application
- Writing unit tests
- Debugging application logic
- Benchmarking performance

**Switch to etcd/Kafka when**:

- Testing multi-peer interaction
- Verifying service discovery
- Testing distributed scenarios
- Preparing for production deployment

**Hybrid approach**:
```bash
# Develop locally
pal run --wal file:dev-wal -cp target/classes com.example.Service

# Test distributed setup
pal run -d localhost:2379 -k localhost:29092 \
  --wal service-wal --rpc auto \
  -cp target/service.jar com.example.Service
```

## Common Issues

### "Chronicle queue does not exist"

**Problem**: Trying to read from non-existent queue

**Solution**: Create it first by writing:
```bash
# Create queue
pal run --wal file:/tmp/my-queue -cp app.jar com.example.Main

# Then read from it
pal run --source-log file:/tmp/my-queue -cp app.jar
```

### "Permission denied"

**Problem**: Cannot write to queue directory

**Solution**: Check permissions:
```bash
ls -la /tmp/my-queue
chmod -R u+rw /tmp/my-queue
```

### "Disk full"

**Problem**: Chronicle queue filled disk

**Solution**: Delete old queues:
```bash
du -sh /tmp/*.queue
rm -rf /tmp/old-queue
```

### AspectJ Weaving Not Working

**Problem**: Methods not intercepted

**Solution**: Verify weaving:
```bash
# Check bytecode
javap -c target/classes/com/example/MyClass.class | grep aspectOf

# Should see AspectJ calls
```

If not, rebuild with AspectJ plugin:
```bash
mvn clean compile
```

## Example: Complete Development Session

```bash
# 1. Create project
mkdir my-pal-app && cd my-pal-app

# 2. Add pom.xml and source code (see above)

# 3. Build
mvn clean install

# 4. Run and test locally
pal run --wal file:dev-wal \
  -cp target/classes com.example.HelloService test1 test2

# 5. Check results
pal print -l file:dev-wal --output-format COMPACT

# 6. Make changes to code
vim src/main/java/com/example/HelloService.java

# 7. Rebuild
mvn compile

# 8. Test changes
pal run --wal file:dev-wal2 \
  -cp target/classes com.example.HelloService test3

# 9. Compare logs
diff <(pal print -l file:dev-wal) <(pal print -l file:dev-wal2)

# 10. When satisfied, package
mvn package

# 11. Ready for distributed testing or deployment
```

## Further Reading

- [Concepts: Log Backends](../concepts/logs.md) - Deep dive into Chronicle vs Kafka
- [Testing with Interception](testing-with-interception.md) - Automated testing patterns
- [Distributed Application Guide](distributed-application.md) - Moving to production
- [CLI Reference](../cli-reference.md) - All `pal run` options
