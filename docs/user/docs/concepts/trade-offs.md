# Trade-offs and Limitations

Every engineering decision involves trade-offs. This page documents PAL's honestly, so you can make an informed decision about whether PAL is right for your use case.

## Build-Time Requirements

- AspectJ weaving must be configured in the build (Maven or Gradle plugin)
- Woven `.class` files are larger than originals
- Build time increases slightly due to the weaving phase
- The `pal-weave` artifact must be declared as an aspect library dependency

## Runtime Overhead

Every quantized operation passes through PAL's dispatch layer. The magnitude of overhead depends on which features are enabled:

- **Standalone mode** (no WAL, no intercepts): Overhead is primarily the AspectJ dispatch cost
- **With WAL writing**: Adds I/O cost per operation (Chronicle Queue is nanosecond-range; Kafka adds millisecond-range async batched writes)
- **With intercepts active**: Adds pattern matching cost per operation, plus possible RPC round-trip for remote callbacks

For CPU-bound tight loops where per-call overhead matters, consider excluding hot-path classes from weaving via AspectJ pointcut configuration.

Stack traces include PAL dispatch frames, which adds noise when debugging. This is inherent to the AspectJ weaving approach.

## Serialization Constraints

- Not all Java objects are serializable; PAL handles primitives, strings, and simple arrays natively
- Complex objects passed through RPC or intercept callbacks use ObjectRef (peer-local references) rather than full serialization
- Lambdas, `ThreadLocal` state, `InputStream`s, and objects with circular references cannot be transparently serialized
- WAL replay with `STUB_FROM_WAL` can reconstruct primitives and strings; complex objects become "phantoms" (operations on them are also stubbed)

## Interception Limitations

- Pattern matching is by class name (Ant-style wildcards), not by Java type hierarchy; intercepting a superclass does not automatically intercept subclasses unless they also match the pattern
- Only classes compiled with PAL's AspectJ weaving can be intercepted as targets; JDK standard library classes (e.g., `java.lang.String`, `java.util.HashMap`) cannot be intercepted. The caller does not need to be woven — invocations via reflection, method references, or unwoven framework dispatchers still fire intercepts on a woven target
- Remote intercept callbacks add network round-trip latency to intercepted operations

## Scope

- **Java only**: PAL is designed for Java. JVM languages like Kotlin and Scala may work with AspectJ but are not tested
- **Single-language message format**: PAL's native message format is Java-specific; cross-language integration uses the JSON-RPC protocol

## What PAL Is Not

- **Not a replacement for production RPC frameworks** (gRPC, REST): PAL's RPC is auxiliary infrastructure supporting intercept callbacks, development workflows, testing, and operational tooling. For production inter-service communication requiring type safety, schema evolution, and high-throughput optimization, use purpose-built RPC frameworks alongside PAL.
- **Not a replacement for APM/observability platforms** (Datadog, New Relic, etc.): PAL captures operations at a different granularity and for different purposes. The WAL is an operation log, not a metrics/tracing pipeline.
- **Not a service mesh**: PAL operates inside the JVM at the bytecode level, not at the network layer. It does not handle load balancing, circuit breaking, or service-to-service network policies.

## Failure Modes

Understanding what happens when infrastructure becomes unavailable:

- **etcd unreachable at startup**: Peer exits immediately with exit code 14 (fail-fast). The peer does not start in degraded mode.
- **Kafka unreachable at startup**: Peer exits immediately with exit code 7 (fail-fast).
- **etcd goes down while peer is running**: Peer's lease expires after TTL (60s default). Intercepts stop updating. Peer continues executing but cannot register new intercepts or be discovered.
- **Kafka goes down while peer is running**: WAL writes will back-pressure. Behavior depends on Kafka producer configuration (blocking vs. dropping).
- **Standalone mode** (no etcd, no Kafka): Peer runs normally with only Chronicle Queue or no logging. This is the simplest and most resilient mode.
