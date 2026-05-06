# Getting Started with PAL

This guide walks you through installing PAL, running your first peer, and inspecting the messages it produces.

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
./mvnw install -DskipTests

export PATH="$(pwd)/bin:$PATH"
```

### Verify Installation

```bash
pal help
```

You should see:

```
Usage: pal [OPTIONS] COMMAND

The message-passing runtime for Java

Options:
  -d, --dir HOST:PORT   PAL directory [env: PAL_DIRECTORY]
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.

Commands:
  run           Run a new peer
  replay        Replay application deterministically from a recorded WAL
  init          Initialize a project for PAL

Management Commands:
  peer          Manage peers
  log           Manage logs
  intercept     Manage intercepts

Shortcuts:
  peers         List peers (shorthand for 'peer ls')
  logs          List logs (shorthand for 'log ls')
  intercepts    List intercepts (shorthand for 'intercept ls')

Run 'pal COMMAND --help' or 'pal COMMAND SUBCOMMAND --help' for more information.
```

## Your First PAL Application

Let's build a simple application and run it with PAL to see operations become messages.

### 1. Create a Project with `pal init`

The fastest way to get started is with `pal init`, which sets up everything you need — build descriptors including AspectJ weaving, sample code, PAL configuration and environment files:

```bash
pal init pal-tutorial
```

The interactive wizard prompts for build tool, identifiers, and a few capability questions. For this tutorial, accept the defaults except: choose **Yes, alongside message pipeline** for JSON-RPC, answer **y** to both the *interceptable* and *intercepting* prompts, and accept `com.example.Main` as the run mode. These choices scaffold the sample classes, RPC policy, and intercept bundle we use throughout the rest of the guide:

```
Welcome to PAL! Let's set up your project.

Build tool (use arrow keys, Enter to confirm)
  ❯ GRADLE
    MAVEN
Project group ID [com.example]:
Project artifact ID [pal-tutorial]:
Project version [1.0]:
Will you expose methods via JSON-RPC? (use arrow keys, Enter to confirm)
    No
  ❯ Yes, alongside message pipeline
    Yes, RPC only (no weaving needed)
Will this app be interceptable by other peers? [y/N] y
Will this app intercept other peers? [y/N] y
Run mode (use arrow keys, Enter to confirm)
    Run as service (no main class)
  ❯ com.example.Main
Will you use Kafka for WAL (write-ahead log)? [y/N]

  ✓ [CREATE] pal-tutorial/build.gradle
  ✓ [CREATE] pal-tutorial/settings.gradle
  ✓ [CREATE] pal-tutorial/src/main/java/com/example/Main.java
  ✓ [CREATE] pal-tutorial/src/main/java/com/example/SampleService.java
  ✓ [CREATE] pal-tutorial/src/main/java/com/example/SampleCallbacks.java
  ✓ [CREATE] pal-tutorial/src/main/java/com/example/Api.java
  ✓ [CREATE] pal-tutorial/config/peer-logging.xml
  ✓ [CREATE] pal-tutorial/config/cli-logging.xml
  ✓ [CREATE] pal-tutorial/config/rpc-policy.yaml
  ✓ [CREATE] pal-tutorial/config/intercept-bundle.yaml
  ✓ [CREATE] pal-tutorial/.env.pal
  ✓ [CREATE] pal-tutorial/infra/docker-compose.yml
  ✓ [CREATE] pal-tutorial/infra/.env
  ✓ [CREATE] pal-tutorial/infra/start.sh
  ✓ [CREATE] pal-tutorial/infra/stop.sh
  ✓ [CREATE] pal-tutorial/gradlew
  ✓ [CREATE] pal-tutorial/gradlew.bat
  ✓ [CREATE] pal-tutorial/gradle/wrapper/gradle-wrapper.properties
  ✓ [CREATE] pal-tutorial/gradle/wrapper/gradle-wrapper.jar
  ✓ [CREATE] pal-tutorial/README.md
Checking for pal-weave 1.0.0...
✓ pal-weave 1.0.0 available

✓ Project initialized!

Next steps:
  1. cd pal-tutorial
  2. ./gradlew build              # Build with AspectJ weaving
  3. infra/start.sh               # Start infrastructure
  4. source .env.pal
  5. pal run -cp build/classes/java/main com.example.Main

See README.md for WAL, interception, JSON-RPC examples, and more.
```

(The real output shows absolute paths; paths are shortened here for readability.)

The result is a complete project with sample code, configuration, and an `infra/` directory that brings up etcd via docker-compose:

```
pal-tutorial/
├── build.gradle
├── config/
│   ├── cli-logging.xml
│   ├── intercept-bundle.yaml
│   ├── peer-logging.xml
│   └── rpc-policy.yaml
├── gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
├── gradlew, gradlew.bat
├── infra/
│   ├── docker-compose.yml
│   ├── start.sh
│   └── stop.sh
├── README.md
├── settings.gradle
└── src/main/java/com/example/
    ├── Api.java
    ├── Main.java
    ├── SampleCallbacks.java
    └── SampleService.java
```

What the generated source files are for:

- **`Main.java`** — entry point used by local mode below. Calls `SampleService.processOrder` to drive a few operations.
- **`SampleService.java`** — the class whose method calls and field accesses the [Interception](#interception-dynamic-behavior-modification) section will intercept.
- **`SampleCallbacks.java`** — public static callback handler that prints intercepted operations to stdout.
- **`Api.java`** — public static methods (`greet`, `add`, `toUpperCase`) invoked over JSON-RPC in the [Distributed Mode](#distributed-mode-cross-peer-rpc) section.

And the configuration files:

- **`config/intercept-bundle.yaml`** — declares an intercept on `SampleService.processOrder` that delegates to `SampleCallbacks.handle`. We apply it in the interception section.
- **`config/rpc-policy.yaml`** — gates which methods are reachable via JSON-RPC. Permits public methods by default; blocks JDK internals.
- **`infra/start.sh`** / **`infra/stop.sh`** — start and stop the bundled etcd container used by distributed mode and interception.

For scripted setups, the same flags are exposed non-interactively. The shortest form that produces the same files as the interactive walkthrough above:

```bash
pal init pal-tutorial --interceptable --intercepting --json-rpc
```

This pre-answers the y/N prompts; you'll still confirm build tool, group/artifact/version, run mode, and Kafka. To skip *every* prompt (CI mode), add `-y`:

```bash
pal init pal-tutorial -y \
  --group-id com.example \
  --artifact-id pal-tutorial \
  --main-class com.example.Main \
  --interceptable --intercepting --json-rpc
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
```

### 3. Run with PAL (Local Mode)

Let's start simple: run with Chronicle Queue (no Kafka/etcd needed).

```bash
# Run the application with PAL
pal run --wal file:/tmp/tutorial-wal -cp build/classes/java/main com.example.Main
```

You should see the application output:

```
Processing order: 1x Laptop @ $999.99
  Discount applied! Total: $899.99
Processing order: 5x Mouse @ $29.99
Processing order: 2x Keyboard @ $79.99
Orders processed: 3
Total revenue: $1309.92
```

**What just happened?**

Every operation — the `processOrder` calls, each `orderCount`/`totalRevenue` field read and write inside `SampleService`, and the `println` calls — was converted to a message and logged to `/tmp/tutorial-wal`. This transformation happened at build time: Gradle's AspectJ weaving task wove PAL's aspects into your compiled `.class` files during the build step. At runtime, these woven classes create messages for each operation, which PAL then writes to the WAL.

### 4. Inspect the Messages

Let's see what PAL captured:

```bash
# Print all messages from the log (tree view)
pal log print file:/tmp/tutorial-wal --tree
```

Output (abbreviated; offsets and object IDs vary):

```
[0] call Main.main
  [1] call SampleService.processOrder
    [2] get SampleService.orderCount
    [3] return int(=0)
    [4] put SampleService.orderCount ⇦ (=1)
    [5] put_done SampleService.orderCount
    [6] get SampleService.totalRevenue
    [7] return double(=0.0)
    [8] put SampleService.totalRevenue ⇦ (=999.99)
    [9] put_done SampleService.totalRevenue
    [10] call PrintStream.println          ("Processing order: 1x Laptop @ $999.99")
    [11] return void
    [12] call SampleService.applyDiscount
    [13] return double(=899.991)
    [14] call PrintStream.println          ("  Discount applied! Total: $899.99")
    [15] return void
  [16] return void
  (two more processOrder subtrees follow the same shape — Mouse and Keyboard,
   neither triggering the discount branch)
  ...
  [N]   get SampleService.orderCount
  [N+1] return int(=3)
  [N+2] call PrintStream.println           ("Orders processed: 3")
  [N+3] return void
  [N+4] get SampleService.totalRevenue
  [N+5] return double(=1309.92)
  [N+6] call PrintStream.println           ("Total revenue: $1309.92")
  [N+7] return void
[N+8] return void
```

Other output formats are available (`--full`, `--json`). See [CLI Reference](cli-reference.md#pal-log-print-print-messages-from-a-log) for details.

**This is the key insight:** Every operation is now a discrete, inspectable message.

### 5. Replay the Execution

You can replay the execution from the log:

```bash
# Replay the recorded execution deterministically
pal replay --wal file:/tmp/tutorial-wal -cp build/classes/java/main com.example.Main
```

You'll see the same output as before, but this time it's being replayed from messages, not executed from Main.main().

**This enables time-travel debugging:** Any execution can be replayed exactly as it happened.

## Adding PAL to an Existing Project

If you already have a Gradle or Maven project, run `pal init` in the project directory. It detects the existing build file and patches it to add PAL weaving:

```bash
cd my-existing-project
pal init
```

```
Detected existing GRADLE project: com.acme:order-service (1.2.0)
Will you expose methods via JSON-RPC? (use arrow keys, Enter to confirm)
  ❯ No
    Yes, alongside message pipeline
    Yes, RPC only (no weaving needed)
Will this app be interceptable by other peers? [y/N]
Will this app intercept other peers? [y/N]
Main class (for pal run) [com.acme.OrderServiceMain]:
Will you use Kafka for WAL (write-ahead log)? [y/N]

  ✓ [PATCH] build.gradle
  ✓ [CREATE] config/peer-logging.xml
  ✓ [CREATE] config/cli-logging.xml
  ✓ [CREATE] .env.pal
  ✓ [CREATE] README.md
Checking for pal-weave 1.0.0...
✓ pal-weave 1.0.0 available
```

A backup of your original build file is created automatically (`build.gradle.backup` or `pom.xml.backup`). Use `--dry-run` to preview the changes before applying them:

```bash
pal init --dry-run
```

For Maven projects, the same flow applies — `pal init` auto-detects `pom.xml`:

```bash
cd my-maven-project
pal init
```

## Manual Setup (Alternative)

If you prefer full control over the build configuration, you can set up PAL manually instead of using `pal init`.

Create a `build.gradle` with the `ajc` weaving task and `pal-weave` dependency (this matches what `pal init` generates):

```groovy
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenLocal()
    mavenCentral()
}

configurations {
    aspectjTools
    aspect
}

dependencies {
    aspectjTools 'org.aspectj:aspectjtools:1.9.24'
    aspect 'io.quasient.pal:pal-weave:1.0.0'
    implementation 'org.aspectj:aspectjrt:1.9.24'
}

// Weave after test so unit tests see unwoven classes. Skip with: ./gradlew build -x weaveClasses
tasks.register('weaveClasses', JavaExec) {
    dependsOn classes
    mustRunAfter test
    mainClass = 'org.aspectj.tools.ajc.Main'
    classpath = configurations.aspectjTools
    args = [
        '-inpath', sourceSets.main.output.classesDirs.asPath,
        '-aspectpath', configurations.aspect.asPath,
        '-d', sourceSets.main.java.destinationDirectory.get().asFile.path,
        '-classpath', sourceSets.main.compileClasspath.asPath,
        '-source', java.sourceCompatibility.toString(),
        '-target', java.targetCompatibility.toString(),
    ]
}

tasks.named('jar') { dependsOn weaveClasses }
```

<details>
<summary>Maven equivalent (pom.xml)</summary>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>pal-tutorial</artifactId>
    <version>1.0</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <pal.version>1.0.0</pal.version>
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

</details>

Then create your Java source files, build with `./gradlew build`, and run with `pal run` as described above.

## Distributed Mode: Cross-Peer RPC

Now let's see how PAL enables cross-peer method invocation.

### 1. Run as a Service Peer

We'll expose the `Api` class that `pal init` already scaffolded under `src/main/java/com/example/Api.java`:

```java
package com.example;

public class Api {
    public static String greet(String name)      { return "Hello, " + name + "!"; }
    public static int    add(int a, int b)       { return a + b; }
    public static String toUpperCase(String txt) { return txt.toUpperCase(); }
}
```

In one terminal, launch a peer that accepts JSON-RPC calls on a known port:

```bash
pal run --wal file:/tmp/service-wal --json-rpc 7070 \
  --rpc-policy config/rpc-policy.yaml \
  -cp build/classes/java/main
```

A few things to notice:

- **No main class** on the command line. Without one, `pal run` keeps the peer alive to handle incoming RPC — no `Thread.sleep` wrapper needed.
- **`--rpc-policy config/rpc-policy.yaml`** points at the policy file `pal init` generated. Its `defaultAction: ALLOW` permits public methods while blocking `Unsafe`, JDK internals, and non-public members — a permissive development profile. Without any policy (or `--rpc-default-action ALLOW` on the command line), every incoming RPC is denied with `RpcAccessDeniedException`.
- **`--json-rpc 7070`** binds the JSON-RPC WebSocket endpoint to a fixed port so the caller can reach it directly. Use `--json-rpc auto` to let PAL pick a free port (you'd then need a directory to advertise it).

### 2. Call the Service Remotely

In another terminal, pipe a JSON-RPC request on stdin to the peer's WebSocket address (`pal peer call` reads one request per line, so keep the JSON on a single line):

```bash
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.Api","method":"greet","args":[{"type":"java.lang.String","value":"World"}]}}' | pal peer call ws://localhost:7070
```

Response:

```json
{"result":{"value":{"type":"java.lang.String","value":"\"Hello, World!\""}, ...}, "id":"1"}
```

Call a method with typed `int` arguments:

```bash
echo '{"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.example.Api","method":"add","args":[{"type":"int","value":10},{"type":"int","value":20}]}}' | pal peer call ws://localhost:7070
```

Response: `"value":"30"` — `add(10, 20) = 30`.

**What happened:**

1. `pal peer call` opened a WebSocket to `ws://localhost:7070` and sent the JSON-RPC request.
2. The peer's RPC policy matched the request against `defaultAction: ALLOW` — permitted.
3. PAL invoked `com.example.Api.add(10, 20)` via reflection.
4. The result `30` came back as a typed JSON-RPC response.

JSON-RPC over stdin gives you full control over argument types — the right choice for any method whose signature is not `static void m(String[])`. For methods that do take a `String[]`, `pal peer call` also accepts positional CLI arguments. See [CLI Reference — Invocation Modes](cli-reference.md#invocation-modes) for both forms.

### 3. Inspect the RPC Messages

```bash
pal log print file:/tmp/service-wal --tree
```

```
[0] call Api.greet
[1] return String@1(="Hello, World!")
[2] call Api.add
[3] return int@2(=30)
```

The WAL captured every incoming RPC as a first-class message — the same substrate you saw in local mode.

> **Note on addressing:** This example uses direct `ws://host:port` addressing because we haven't started etcd (the PAL directory). If you run a directory and start the peer with `-d localhost:2379 -n service`, you can call it by name instead: `pal peer call -d localhost:2379 service`. Running PAL at scale typically means multiple peers discovering each other through the directory; the direct form here is for the local-development workflow.

## Interception: Dynamic Behavior Modification

Now let's intercept method calls at runtime — without modifying any application code. `pal init` already scaffolded everything we need:

- **`SampleService.processOrder`** — the target method, already called from `Main.java`.
- **`SampleCallbacks.handle`** — a public static callback that prints intercepted operations to stdout.
- **`config/intercept-bundle.yaml`** — declares the intercept that wires the two together.

We'll start the directory, launch a callback peer, apply the bundle, then run `Main` and watch the callback fire.

### 1. Start the Directory

Interception uses the directory (etcd) to register intercepts and to resolve callback peers by name. The project's `infra/` directory contains a docker-compose for exactly this:

```bash
infra/start.sh
```

This brings up an etcd reachable at `localhost:2379`. Run `infra/stop.sh` when you're done.

### 2. Launch the Callback Peer

The scaffolded bundle's `peer:` field is the project's artifact ID (`pal-tutorial`), so the callback peer must register under that name. In a new terminal, start a peer with no main class — it stays alive to receive callbacks — and a `--zmq-rpc` endpoint so the application peer can reach it:

```bash
pal run -d localhost:2379 -n pal-tutorial --zmq-rpc auto \
  --wal file:/tmp/cb-wal \
  -cp build/classes/java/main
```

### 3. Apply the Intercept Bundle

In another terminal, apply the bundle that `pal init` generated:

```bash
pal intercept apply -d localhost:2379 config/intercept-bundle.yaml
```

You should see:

```
Applied: 1 created, 0 skipped, 0 failed
```

For reference, that bundle declares (open `config/intercept-bundle.yaml` to see):

```yaml
bundle: "pal-tutorial-intercepts"
defaults:
  peer: "pal-tutorial"
intercepts:
  - target: com.example.SampleService.processOrder
    type: BEFORE
    callback:
      class: com.example.SampleCallbacks
      method: handle
```

### 4. Run `Main` with Interception Enabled

Run the application again, this time registered in the directory and marked `--interceptable` so it picks up the registered intercept:

```bash
pal run -d localhost:2379 --interceptable -cp build/classes/java/main com.example.Main
```

Main runs the same three `processOrder` calls as before, then exits.

### 5. Observe the Callback

In the callback peer's terminal, three intercept callbacks now appear — one per `processOrder` call:

```
[intercept] BEFORE callback, args=[Laptop, 1, 999.99]
[intercept] BEFORE callback, args=[Mouse, 5, 29.99]
[intercept] BEFORE callback, args=[Keyboard, 2, 79.99]
```

That output came from `SampleCallbacks.handle` running on the callback peer, in response to method calls on the application peer — no edits to `SampleService.java`, no rebuild, no restart needed to attach the observer.

A `BEFORE` intercept observes but does not alter the call. Switch the bundle's `type:` to `AROUND` and use `ctx.setReturnValue(...)` (see [Interception](concepts/interception.md#around)) to mock or transform results instead. Re-apply with `pal intercept apply` — the bundle is idempotent.

### 6. Remove the Intercept

Detach the callback — live, no peer restart required:

```bash
pal intercept rm -d localhost:2379 -f config/intercept-bundle.yaml
```

Subsequent runs of `Main` execute un-intercepted.

### 7. Stop the Infrastructure

When you're done, shut down the callback peer (Ctrl-C in its terminal) and stop etcd:

```bash
infra/stop.sh
```

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

# With JSON-RPC on stdin (full control over types)
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{
  "type":"com.example.MyClass","method":"myMethod",
  "args":[{"type":"java.lang.String","value":"\"arg1\""}]
}}' | pal peer call -d localhost:2379 my-service
```

### Clean Up

```bash
# Remove a peer from directory
pal peer rm -d localhost:2379 my-peer-uuid

# Remove a log from directory (doesn't delete Kafka topic)
pal log rm -d localhost:2379 my-log

# Remove a Chronicle log (deletes files and, if registered, the directory entry)
pal log rm file:/tmp/tutorial-wal
```

## Troubleshooting

### "ERROR_UNREACHABLE_ETCD" (exit code 14)

```bash
# Check if etcd is running
curl http://localhost:2379/health

# If not, start it
infra/start.sh
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
infra/start.sh
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

Common causes:

- **Missing AspectJ plugin**: Ensure your Gradle or Maven build includes the AspectJ weaving plugin with `pal-weave` as an aspect library.
- **Class not woven**: Only classes processed by the AspectJ plugin will have PAL's dispatch. Verify with `javap -c` as shown above.
- **Wrong plugin configuration**: The aspect library must reference `pal-weave`, not just have it as a dependency.
- **Incremental build stale**: Try a clean build (`./gradlew clean build` or `mvn clean install`) to ensure all classes are freshly woven.

### Interception Not Working

1. Ensure class was compiled with AspectJ weaving.
2. Ensure peer started with `--interceptable` flag.
3. Ensure etcd is up.
4. Verify intercept is registered (see `pal intercept ls`)
5. Ensure the callback peer is launched with `--zmq-rpc`
6. Check intercept pattern matches class/method names.
7. Enable debug logging in logback.xml:
   ```xml
   <logger name="io.quasient.pal.core.intercept.InterceptMatcher" level="DEBUG"/>
   ```

### Infrastructure Failure Modes

For detailed information about what happens when etcd or Kafka become unavailable (at startup or while running), see [Trade-offs and Limitations — Failure Modes](understanding-pal.md#failure-modes).

## Next Steps

Now that you've experienced PAL's core capabilities:

1. **Understand the concepts:** Read [Understanding PAL](understanding-pal.md) for deep dive.
2. **Explore use cases:** See [Use Cases](use-cases.md) for your role (developer, SRE, architect).
3. **Learn the details:**
   - [Peers and Logs](concepts/peers-and-logs.md)
   - [RPC](concepts/rpc.md)
   - [Interception](concepts/interception.md)
   - [Log Backends](concepts/logs.md)
4. **Follow guides:**
   - [Local Development](guides/local-development.md)
   - [Distributed Application](guides/distributed-application.md)

## Summary

You've learned:

- ✓ How to install and set up PAL
- ✓ How to scaffold a project with `pal init` (or configure manually)
- ✓ How to add PAL to an existing project with `pal init`
- ✓ How to compile applications with AspectJ weaving
- ✓ How operations become messages (quantization)
- ✓ How to run peers locally (Chronicle) and distributed (Kafka/etcd)
- ✓ How to inspect messages in logs
- ✓ How to call remote methods via RPC
- ✓ How to replay executions from logs
- ✓ The basics of interception for dynamic behavior

PAL converts your operations into messages. Everything else flows from that simple transformation.

## Need Help?

Join us on [Discord](https://discord.gg/k4XsMyUA) — ask questions, share feedback, or just say hello.
