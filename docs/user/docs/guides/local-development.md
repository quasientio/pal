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

## Quick Setup with `pal init`

The fastest way to set up a local PAL project is with `pal init --mode local`:

**Maven:**

```bash
pal init my-local-app --mode local
cd my-local-app
source .env.pal
mvn compile
pal run --wal file:./wal -cp target/classes com.example.Main
```

**Gradle:**

```bash
pal init my-local-app --mode local --build-tool gradle
cd my-local-app
source .env.pal
gradle build
pal run --wal file:./wal -cp build/classes/java/main com.example.Main
```

For an existing project, run `pal init` in the project directory to patch your build file:

```bash
cd my-existing-project
pal init --mode local
```

This adds the `pal-weave` dependency and AspectJ weaving plugin to your `pom.xml` or `build.gradle` automatically (a backup is created before patching). Use `--dry-run` to preview the changes first.

For full control over the build configuration, see the [Manual Setup](#manual-setup) section below.

## Prerequisites

**Required**:

- JDK 17 or later (set `JAVA_HOME`)
- Maven 3.x or Gradle
- PAL installed and on PATH

**Not required**:

- Docker
- etcd
- Kafka
- Network configuration

## Verify PAL Installation

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
pal log print file:/tmp/dev-wal --compact
```

See all operations logged:

```
offset=0 id=abc123 message=HelloService.processMessage("hello")
offset=1 id=abc124 message=HelloService.processMessage("world")
```

**Note**: The `file:` prefix enables **direct mode** CLI access - you can read Chronicle logs without needing etcd or the PAL directory. This is perfect for local development!

### Direct Mode CLI Commands

When developing locally with Chronicle Queue, all CLI commands support direct mode access:

```bash
# Print messages from Chronicle log
pal log print file:/tmp/dev-wal --full

# Call method and write to Chronicle log
pal log call file:/tmp/dev-wal com.example.HelloService processMessage "test"

# Remove Chronicle log
pal log rm file:/tmp/dev-wal
```

**Benefits of direct mode**:

- No etcd or PAL directory needed
- Simple file paths (absolute or relative)
- Works immediately after creating a peer
- Perfect for scripts and automation

For distributed systems with multiple peers, use **registry mode** with the `-d` option to enable service discovery. See the [Distributed Application Guide](distributed-application.md) for details.

### Replay from WAL

Stop the peer (Ctrl-C), then replay:

```bash
pal run --source-log file:/tmp/dev-wal \
  -cp target/myapp-1.0-SNAPSHOT.jar \
  com.example.HelloService
```

All logged operations are re-executed.

### Deterministic Replay

For verifying that code changes don't alter behavior, use `pal replay` instead. This re-runs the application from `main()` and verifies every operation against the recorded WAL:

```bash
pal replay --wal file:/tmp/dev-wal \
  -cp target/myapp-1.0-SNAPSHOT.jar \
  com.example.HelloService hello world
```

Exit code `0` means the execution matched the WAL exactly. Exit code `2` means divergences were detected. See the [Deterministic Replay Guide](deterministic-replay.md) for full details.

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

- **Main class**: `io.quasient.pal.cli.Pal`
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
    <logger name="io.quasient.pal.core.dispatcher" level="DEBUG"/>

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
pal log print file:/tmp/live-wal -f --full
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

# Or use deterministic replay to verify behavior
pal replay --wal file:session1 -cp target/classes com.example.Main input1
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
  --wal service-wal --json-rpc auto \
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

## Manual Setup

If you prefer full control over the build configuration instead of using `pal init`, you can set up PAL weaving manually.

**Maven (`pom.xml`):**

```xml
<dependencies>
    <dependency>
        <groupId>io.quasient.pal</groupId>
        <artifactId>pal-weave</artifactId>
        <version>1.0.0-SNAPSHOT</version>
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

**Gradle (`build.gradle`):**

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

Build with `mvn clean install` (Maven) or `gradle build` (Gradle). Your application is then ready to run with PAL.

## Example: Complete Development Session

```bash
# 1. Create project with pal init
pal init my-pal-app --mode local
cd my-pal-app
source .env.pal

# 2. Build
mvn compile

# 3. Run and test locally
pal run --wal file:dev-wal \
  -cp target/classes com.example.Main

# 4. Check results
pal log print file:dev-wal --compact

# 5. Make changes to code
vim src/main/java/com/example/SampleService.java

# 6. Rebuild
mvn compile

# 7. Test changes
pal run --wal file:dev-wal2 \
  -cp target/classes com.example.Main

# 8. Compare logs
diff <(pal log print file:dev-wal) <(pal log print file:dev-wal2)

# 9. When satisfied, package
mvn package

# 10. Ready for distributed testing or deployment
```

## Further Reading

- [Concepts: Log Backends](../concepts/logs.md) - Deep dive into Chronicle vs Kafka
- [Deterministic Replay](deterministic-replay.md) - Verify code changes against recorded WALs
- [Testing with Interception](testing-with-interception.md) - Automated testing patterns
- [Distributed Application Guide](distributed-application.md) - Moving to production
- [CLI Reference](../cli-reference.md) - All `pal run` options
