# JVM Configuration

PAL runs as a standard Java process launched by the `bin/pal` script. The script sets sensible defaults for heap sizing, garbage collection, and module exports, but provides three layers of customization for users who need to tune JVM behavior.

## Precedence

JVM options are assembled in this order. Later layers override earlier ones (last wins):

1. **Chronicle Queue module exports** — always applied, required for PAL to function
2. **Category environment variables** — replaceable defaults for heap, GC, and JMX (`pal run` only)
3. **`pal.vmoptions` file** — persistent per-installation configuration
4. **`PAL_JAVA_OPTS` environment variable** — one-off overrides, always wins

## Category Environment Variables

These variables apply only to the `pal run` command (peer processes). Setting a variable **replaces** its default entirely, giving full control over that category without affecting others.

### PAL_HEAP_OPTS

Heap sizing options. Default: `-Xmx1g`

```bash
# Increase heap to 4 GB with a 2 GB initial size
export PAL_HEAP_OPTS="-Xmx4g -Xms2g"
pal run -cp app.jar com.example.Main
```

### PAL_GC_OPTS

Garbage collector selection and tuning. Default:

```
-server -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled
```

```bash
# Switch to ZGC for low-latency workloads
export PAL_GC_OPTS="-XX:+UseZGC -XX:+ZGenerational"
pal run -cp app.jar com.example.Main
```

### PAL_JMX_OPTS

JMX remote monitoring configuration. Default: built automatically from `PAL_JMX_HOST` and `PAL_JMX_PORT` if both are set, otherwise empty.

```bash
# Enable JMX with convenience variables (builds PAL_JMX_OPTS for you)
export PAL_JMX_HOST=localhost
export PAL_JMX_PORT=9999
pal run -cp app.jar com.example.Main

# Or set the full JMX configuration directly
export PAL_JMX_OPTS="-Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9999 \
  -Dcom.sun.management.jmxremote.authenticate=true \
  -Dcom.sun.management.jmxremote.ssl=true \
  -Dcom.sun.management.jmxremote.password.file=/path/to/jmxremote.password"
pal run -cp app.jar com.example.Main
```

Setting `PAL_JMX_OPTS` directly takes precedence over `PAL_JMX_HOST`/`PAL_JMX_PORT`.

## pal.vmoptions File

For persistent JVM configuration that doesn't require environment variables, create a `pal.vmoptions` file in the PAL config directory:

```
config/pal.vmoptions
```

The file contains one JVM option per line. Blank lines and lines starting with `#` are ignored.

```properties
# config/pal.vmoptions

# Heap
-Xmx4g
-Xms2g

# Remote debugging (suspend=n means don't wait for debugger)
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

# Application properties
-Dmy.config.path=/etc/pal/app.properties
```

An example file with all defaults commented out ships at `config/pal.vmoptions.example`. Copy it to `config/pal.vmoptions` and uncomment the options you need.

Options in this file are loaded **after** the category environment variables and **before** `PAL_JAVA_OPTS`, so they can override category defaults but are themselves overridden by `PAL_JAVA_OPTS`.

## PAL_JAVA_OPTS Environment Variable

A catch-all for any JVM flags, system properties, or agent options. Appended **last**, so it always takes precedence.

```bash
# Add a system property for a single run
PAL_JAVA_OPTS="-Dmy.feature.enabled=true" pal run -cp app.jar com.example.Main

# Attach a profiling agent
PAL_JAVA_OPTS="-agentpath:/opt/async-profiler/lib/libasyncProfiler.so=start,file=profile.jfr" \
  pal run -cp app.jar com.example.Main

# Enable remote debugging
PAL_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" \
  pal run -cp app.jar com.example.Main
```

## Common Scenarios

### Production: increase heap and tune GC

```bash
export PAL_HEAP_OPTS="-Xmx8g -Xms8g"
export PAL_GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled -XX:G1HeapRegionSize=16m"
pal run -d prod-etcd:2379 -k prod-kafka:9092 --wal my-wal -cp app.jar com.example.Main
```

### Local development: smaller heap, fast startup

```bash
export PAL_HEAP_OPTS="-Xmx512m"
pal run --wal file:/tmp/dev-wal --json-rpc auto -cp build/classes/java/main com.example.Main
```

### Low-latency: switch to ZGC

```bash
export PAL_GC_OPTS="-XX:+UseZGC -XX:+ZGenerational"
export PAL_HEAP_OPTS="-Xmx4g"
pal run -cp app.jar com.example.Main
```

### Debugging: attach a debugger

```bash
PAL_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" \
  pal run -cp app.jar com.example.Main
```

Then connect from your IDE to `localhost:5005`.

### JMX monitoring

```bash
export PAL_JMX_HOST=localhost
export PAL_JMX_PORT=9999
pal run -cp app.jar com.example.Main
```

Then connect with `jconsole localhost:9999` or VisualVM.

## Logging Configuration

PAL uses [shaded](https://maven.apache.org/plugins/maven-shade-plugin/) Logback for its own internal logging, so it never interferes with your application's logging — even if your application also uses SLF4J and Logback. The two logging systems are completely independent.

PAL's logging is controlled by two XML configuration files:

| File | Controls | Environment Variable |
|------|----------|---------------------|
| `peer-logging.xml` | `pal run` (peer runtime) | `PAL_PEER_LOGGING_CONFIG` |
| `cli-logging.xml` | CLI commands (`pal log print`, `pal call`, etc.) | `PAL_CLI_LOGGING_CONFIG` |

Both files are generated by `pal init` into the `config/` directory and wired up in `.env.pal`. If neither environment variable is set, PAL falls back to built-in defaults (peer logs to `logs/peer.log`, CLI logs to `logs/cli.log`).

Because PAL uses shaded Logback, configuration files must use PAL's appender classes (`io.quasient.pal.common.logging.PeerFileAppender`, `io.quasient.pal.common.logging.PeerConsoleAppender`) and PAL's logger namespaces (e.g., `io.quasient.pal`, `io.quasient.pal.shd.apache.kafka`). See `config/peer-logging.xml.example` and `config/cli-logging.xml.example` for the full structure.

## Other Environment Variables

These are not JVM options per se, but affect how the `pal` script launches the Java process:

| Variable | Description |
|----------|-------------|
| `JAVA_AGENT` | Path to a Java agent JAR (added as `-javaagent:<path>`) |
| `PAL_PEER_LOGGING_CONFIG` | Path to PAL peer Logback configuration file |
| `PAL_CLI_LOGGING_CONFIG` | Path to PAL CLI Logback configuration file |

## Reference: Environment Variable Summary

| Variable | Scope | Default | Description |
|----------|-------|---------|-------------|
| `PAL_HEAP_OPTS` | `pal run` | `-Xmx1g` | Heap sizing flags |
| `PAL_GC_OPTS` | `pal run` | G1GC, 200ms pause | GC selection and tuning |
| `PAL_JMX_OPTS` | `pal run` | From `PAL_JMX_HOST`/`PORT` | Full JMX configuration |
| `PAL_JMX_HOST` | `pal run` | _(unset)_ | JMX hostname (convenience) |
| `PAL_JMX_PORT` | `pal run` | _(unset)_ | JMX port (convenience) |
| `PAL_JAVA_OPTS` | all commands | _(empty)_ | Catch-all, appended last |
| `JAVA_AGENT` | all commands | _(unset)_ | Java agent JAR path |
| `PAL_PEER_LOGGING_CONFIG` | `pal run` | _(unset)_ | PAL peer Logback config file |
| `PAL_CLI_LOGGING_CONFIG` | CLI commands | _(unset)_ | PAL CLI Logback config file |
