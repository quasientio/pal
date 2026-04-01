# Interception

Interception lets you insert callbacks before, after, or around any method call at runtime - without changing code or recompiling.

## What is Interception?

Imagine you want to know every time a method is called in a running application:

- To verify it's called with correct arguments
- To measure how long it takes
- To check application state before execution
- To mock the return value for testing

PAL's interception system lets you register these callbacks **dynamically** while the application runs.

## How Interception Works

1. Your application is compiled with AspectJ weaving (automatic with `pal run`)
2. You register an intercept pattern in the directory
3. PAL matches method calls against the pattern
4. When matched, PAL sends a callback message to your peer
5. Your peer receives the callback and can inspect/modify behavior

**Key**: You don't modify the target code. You register intercepts from outside.

## Intercept Types

### BEFORE

Callback executes **before** the method, synchronously:

```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    myPeerUuid,
    InterceptType.BEFORE,
    "com.example.Calculator",
    "com.example.CalculatorCallback",
    "handle",
    new InterceptableMethodCall("add", Collections.emptyList()));
```

**Use cases**:

- Verify method is called with expected arguments
- Check preconditions
- Log entry to method
- Authorization checks

**Timing**: Blocks target method until callback completes.

### AFTER

Callback executes **after** the method completes, synchronously:

```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    myPeerUuid,
    InterceptType.AFTER,
    "com.example.Calculator",
    "com.example.CalculatorCallback",
    "handle",
    new InterceptableMethodCall("add", Collections.emptyList()));
```

**Use cases**:

- Verify return value
- Override return value
- Log method exit
- Collect metrics

**Timing**: Blocks until callback completes (synchronous). Use `AFTER_ASYNC` for fire-and-forget callbacks.

### AROUND

Callback **wraps** the method execution with before/after logic. Call `ctx.proceed()` to execute the method (or next layer in the chain), or skip execution entirely:

```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    myPeerUuid,
    InterceptType.AROUND,
    "com.example.Calculator",
    "com.example.CalculatorCallback",
    "handle",
    new InterceptableMethodCall("add", Collections.emptyList()));
```

**Use cases**:

- Mock return values in tests
- Cache results (skip expensive computation)
- Transform arguments before and return values after execution
- Circuit breaker pattern

**Timing**: Callback decides whether to call `proceed()` (execute method) or `skipProceed()` (return custom value).

**Key methods**:

- `ctx.proceed()` - Execute method, returns `ProceedResult` with return value or exception
- `ctx.setReturnValue(value)` + `skipProceed()` - Skip method and return custom value
- `ctx.setArg(index, value)` - Modify arguments before `proceed()`

### BEFORE_ASYNC and AFTER_ASYNC

Fire-and-forget callbacks that don't block execution:

```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    myPeerUuid,
    InterceptType.BEFORE_ASYNC,  // or AFTER_ASYNC
    "com.example.Service",
    "com.example.ServiceCallback",
    "handle",
    new InterceptableMethodCall("process", Collections.emptyList()));
```

**Use cases**:

- Telemetry and monitoring
- Audit logging
- Metrics collection
- Notifications

**Timing**: Callback is sent but caller doesn't wait for response.

**Limitations**: Cannot modify arguments, return values, or throw exceptions (fire-and-forget).

## Multiple Intercepts and Ordering

When multiple intercepts match the same operation, they execute in a specific order determined by three factors:

### Execution Order

1. **Local vs Remote**: Local intercepts (callback peer = intercepted peer) always execute **before** remote intercepts
2. **Priority**: Within each local/remote group, intercepts with lower priority values execute first (default priority is `0`)
3. **Registration order**: Intercepts with the same priority execute in the order they were registered (tie-breaker)

```
BEFORE phase:
  1. Local BEFORE callbacks  (sorted by ascending priority)
  2. Remote BEFORE callbacks (sorted by ascending priority)

AROUND phase (onion model):
  3. Local AROUND callbacks  (lower priority = outermost layer)
  4. Remote AROUND callbacks (lower priority = outermost layer)
  [Method Execution - innermost]
  (Return values propagate outward)

AFTER phase:
  5. Local AFTER callbacks   (sorted by ascending priority)
  6. Remote AFTER callbacks  (sorted by ascending priority)
```

### Setting Priority

Pass the `priority` parameter when creating an `InterceptRequest`. Lower values execute first. The default is `0`.

```java
// Security check: runs first (low priority value)
InterceptRequest<InterceptableMethodCall> securityIntercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.SecurityCallback",
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false, null, null,
    -100);  // priority: runs before default intercepts

// Application logic: runs at default priority
InterceptRequest<InterceptableMethodCall> appIntercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.AppCallback",
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()));
    // priority defaults to 0

// Logging: runs last (high priority value)
InterceptRequest<InterceptableMethodCall> loggingIntercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.LoggingCallback",
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false, null, null,
    100);  // priority: runs after default intercepts
```

**Execution order**: Security (p=-100) → Application (p=0) → Logging (p=100)

### Recommended Priority Ranges

Use widely-spaced values to leave room for inserting new intercepts between existing ones:

| Range | Suggested Use |
|-------|---------------|
| -1000 | Infrastructure-level (framework internals) |
| -100 | Security checks, authorization |
| 0 | Default — general-purpose intercepts |
| 100 | Logging, metrics, monitoring |
| 1000 | Audit trail, compliance recording |

**Tip**: For deterministic ordering, set priority explicitly rather than relying on registration order. Registration order depends on the timing of etcd events, which may vary.

### AROUND Chain and Priority

For AROUND intercepts, priority determines the layer in the onion model:

- **Lower priority = outermost layer** (BEFORE logic runs first, AFTER logic runs last)
- **Higher priority = innermost layer** (closest to the actual method)

```
┌─ AROUND priority=-100 (outermost) ──────────────────┐
│  BEFORE logic runs FIRST                             │
│  ctx.proceed() ─────────────────────────────────────▶│
│    ┌─ AROUND priority=0 ────────────────────────┐    │
│    │  ctx.proceed() ───────────────────────────▶ │    │
│    │    ┌─ AROUND priority=100 (innermost) ──┐   │    │
│    │    │  ctx.proceed() ──────────────────────────▶ [METHOD]
│    │    │  AFTER logic                       │   │    │
│    │    └────────────────────────────────────┘   │    │
│    │  AFTER logic                                │    │
│    └─────────────────────────────────────────────┘    │
│  AFTER logic runs LAST                                │
└───────────────────────────────────────────────────────┘
```

### AROUND Chaining (Onion Model)

Multiple AROUND intercepts form a **chain** where each `proceed()` invokes the next layer, not the method directly:

```
┌─ Local AROUND #1 (outermost) ───────────────────────────┐
│  BEFORE logic                                            │
│  ctx.proceed() ─────────────────────────────────────────┼──▶
│    ┌─ Local AROUND #2 ──────────────────────────────┐   │
│    │  BEFORE logic                                   │   │
│    │  ctx.proceed() ────────────────────────────────┼───┼──▶
│    │    ┌─ Remote AROUND #1 (innermost) ────────┐   │   │
│    │    │  ctx.proceed() ───────────────────────┼───┼───┼──▶ [METHOD]
│    │    │  (return flows back)                  │   │   │
│    │    │  AFTER logic                          │   │   │
│    │    └───────────────────────────────────────┘   │   │
│    │  AFTER logic (can modify return)               │   │
│    └────────────────────────────────────────────────┘   │
│  AFTER logic (can modify return)                        │
└─────────────────────────────────────────────────────────┘
```

**Key behaviors**:

- **Argument mutations propagate inward**: Each layer sees cumulative mutations from outer layers
- **Return values propagate outward**: Each layer can modify the return value from inner layers
- **Skip affects all inner layers**: When any layer calls `skipProceed()`, all inner layers (including the method) are bypassed

### Argument Mutation in AROUND Chains

```java
// Chain: Outer → Inner → Method
// Outer sets arg[0] = 10, calls proceed()
// Inner sees arg[0] = 10, sets arg[0] = 20, calls proceed()
// Method sees arg[0] = 20
```

### Return Value Override in AROUND Chains

```java
// Method returns 5
// Inner receives 5, returns 5 + 10 = 15
// Outer receives 15, returns 15 * 2 = 30
// Final result = 30
```

## Registering Intercepts

### From Java Code

```java
// 1. Connect to directory
PalDirectory directory = new PalDirectory("localhost:2379");

// 2. Create intercept request
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    myCallbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.ServiceCallback",
    "handleProcessRequest",
    new InterceptableMethodCall("processRequest", Collections.emptyList()));

// 3. Register
directory.createIntercept(intercept);

// 4. Callbacks will now be sent to your peer
```

### Pattern Matching

Use ant-style patterns to match classes and methods:

#### Exact Match (all overloads)
```java
// clazz and InterceptableMethodCall name
"com.example.Calculator", ..., new InterceptableMethodCall("add", Collections.emptyList())
```
Matches all overloads of `Calculator.add` regardless of parameter types. An empty parameter list acts as a wildcard.

#### Wildcard
```java
// clazz and InterceptableMethodCall name
"com.example.*", ..., new InterceptableMethodCall("process*", Collections.emptyList())
```
Matches all classes in `com.example` package with methods starting with "process" (any parameter types).

#### Exact Match with Parameter Types
```java
// clazz and InterceptableMethodCall name + parameterTypes
"com.example.Calculator", ..., new InterceptableMethodCall("add", Arrays.asList("int", "int"))
```
Matches only `Calculator.add(int, int)`. Other overloads like `add(double, double)` are not intercepted. Parameter types must be fully qualified (e.g., `java.lang.String`, not `String`). Omit parameter types (empty list) to match all overloads.

#### Recursive
```java
// clazz and InterceptableMethodCall name
"com.example.**.*", ..., new InterceptableMethodCall("*", Collections.emptyList())
```
Matches all classes in `com.example` and subpackages, all methods (any parameter types).

## Receiving Callbacks

### Setup Callback Peer

```java
// 1. Start peer with RPC
public class CallbackPeer {
    public static void main(String[] args) {
        // Your peer will receive callback messages
        System.out.println("Callback peer ready");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }

    // Callback methods get invoked automatically
    public void handleCallback(ExecMessage msg) {
        System.out.println("Method called: " + msg.getMethod());
        System.out.println("Arguments: " + Arrays.toString(msg.getArgs()));
    }
}
```

Run it:
```bash
pal run -d localhost:2379 --json-rpc auto -n callback-peer \
  -cp callback.jar com.example.CallbackPeer
```

### Processing Callbacks

When a matched method is called, your callback peer receives an `ExecMessage` with:

- `sourceClass`: Class name (e.g., "com.example.Calculator")
- `method`: Method name (e.g., "add")
- `args`: Arguments array
- `sourcePeer`: UUID of peer that called the method
- `timestamp`: When it was called

**BEFORE callbacks** also let you:

- Inspect arguments before execution
- Skip execution (AROUND type)
- Modify behavior

**AFTER callbacks** also include:

- `returnValue`: What the method returned
- `exception`: If an exception was thrown

## Common Use Cases

### Testing: Verify Method Calls

```java
@Test
public void testServiceCalledWithCorrectArgs() {
    // 1. Start application under test
    UUID appPeerUuid = startApplicationPeer();

    // 2. Start callback peer
    UUID callbackPeerUuid = startCallbackPeer();

    // 3. Register intercept
    InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
        UUID.randomUUID(),
        callbackPeerUuid,
        InterceptType.BEFORE,
        "com.example.Service",
        "com.example.ServiceCallback",
        "handle",
        new InterceptableMethodCall("processRequest", Collections.emptyList()));
    directory.createIntercept(intercept);

    // 4. Trigger application behavior
    app.doSomething();

    // 5. Verify callback was received
    List<ExecMessage> callbacks = getReceivedCallbacks();
    assertEquals(1, callbacks.size());
    assertEquals("processRequest", callbacks.get(0).getMethod());
    assertArrayEquals(expectedArgs, callbacks.get(0).getArgs());
}
```

### Monitoring: Track Performance

```java
// Measure method execution time
InterceptRequest<InterceptableMethodCall> beforeIntercept = new InterceptRequest<>(
    UUID.randomUUID(),
    monitorPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.MonitorCallback",
    "handleBeforeCallback",
    new InterceptableMethodCall("*", Collections.emptyList()));

InterceptRequest<InterceptableMethodCall> afterIntercept = new InterceptRequest<>(
    UUID.randomUUID(),
    monitorPeerUuid,
    InterceptType.AFTER,
    "com.example.Service",
    "com.example.MonitorCallback",
    "handleAfterCallback",
    new InterceptableMethodCall("*", Collections.emptyList()));

// In monitor peer:
Map<String, Long> startTimes = new ConcurrentHashMap<>();

public void handleBeforeCallback(ExecMessage msg) {
    startTimes.put(msg.getId(), System.nanoTime());
}

public void handleAfterCallback(ExecMessage msg) {
    long startTime = startTimes.remove(msg.getId());
    long duration = System.nanoTime() - startTime;
    System.out.println(msg.getMethod() + " took " + (duration / 1_000_000) + "ms");
}
```

### Debugging: Audit Trail

```java
// Log all method calls
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    auditPeerUuid,
    InterceptType.BEFORE,
    "com.example.**.*",
    "com.example.AuditCallback",
    "handleCallback",
    new InterceptableMethodCall("*", Collections.emptyList()));

// In audit peer:
public void handleCallback(ExecMessage msg) {
    log.info("Called: {}.{}({})",
        msg.getSourceClass(),
        msg.getMethod(),
        Arrays.toString(msg.getArgs()));
}
```

### Testing: Mock Return Values

```java
// Mock expensive operation
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    mockPeerUuid,
    InterceptType.AROUND,
    "com.example.DatabaseService",
    "com.example.MockCallback",
    "handleAroundCallback",
    new InterceptableMethodCall("queryDatabase", Collections.emptyList()));

// In mock peer:
public Object handleAroundCallback(ExecMessage msg) {
    // Return mock data instead of hitting database
    return mockData;
}
```

## Enabling Interception

### Application Must Be Woven

Your application needs AspectJ weaving for interception to work. With `pal run`, this is automatic.

If building a JAR manually, configure AspectJ in your `pom.xml`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <configuration>
        <aspectLibraries>
            <aspectLibrary>
                <groupId>io.quasient.pal</groupId>
                <artifactId>pal-weave</artifactId>
            </aspectLibrary>
        </aspectLibraries>
    </configuration>
</plugin>
```

### Peer Must Be Interceptable

Start peer with interception enabled:

```bash
pal run -d localhost:2379 --interceptable \
  -cp app.jar com.example.App
```

The `--interceptable` flag enables the intercept matcher service.

## Intercept Activation Safety

By default, PAL waits for in-flight operations to finish before activating a new intercept. This applies to methods, constructors, and field operations. It ensures no execution sees a partially-activated intercept -- for example, a BEFORE callback fires but the method was already past that point.

### How It Works

When a new intercept is registered, PAL:

1. **Fences** the matching operations so no new calls can start
2. **Waits** for all currently executing matching calls to complete (drain)
3. **Activates** the intercept once all in-flight calls finish
4. **Unfences** so new calls proceed with the intercept active

This guarantees that every call either completes entirely without the intercept or executes entirely with it -- never a mix.

Tracking is **per-operation-signature**: when parameter types are specified, fencing `add(int)` does **not** block `add(int, int)` -- only the exact overload being intercepted is fenced. When parameter types are omitted (wildcard), all overloads are fenced. Similarly, constructors and field operations are tracked separately from methods, even if they share a name.

### Enabling and Configuring

In-flight tracking is enabled by default. To disable it (immediate activation for all intercepts):

```bash
pal run --in-flight-tracking=false -d localhost:2379 -cp app.jar
```

The **drain timeout** controls how long PAL waits for in-flight calls to complete. If the timeout expires, the intercept is **not** activated:

```bash
# Default: 5000ms. Increase for slow methods:
pal run --drain-timeout-ms 10000 -d localhost:2379 -cp app.jar
```

Both settings are also available as environment variables: `PAL_IN_FLIGHT_TRACKING` and `PAL_DRAIN_TIMEOUT_MS`.

### Per-Intercept Override: forceImmediate

Individual intercepts can bypass the drain by setting `forceImmediate` to `true`:

```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.HangingService",
    "com.example.EmergencyCallback",
    "handle",
    new InterceptableMethodCall("blockingCall", Collections.emptyList()),
    true /* forceImmediate */);
```

**When to use**: Emergency hot-patches where the target method is hanging or stuck, monitoring intercepts where strict activation safety isn't needed, or any case where you need the intercept active immediately regardless of in-flight calls.

### When to Disable Globally

Disable in-flight tracking (`--in-flight-tracking=false`) when:

- Running in a controlled test environment where race conditions aren't a concern
- Performance is critical and you accept the small risk of partial activation
- All your intercepts are `BEFORE_ASYNC` or `AFTER_ASYNC` (fire-and-forget)

## Intercept TTL and Lease Management

Intercepts can be created with a **TTL (Time-To-Live)** so they automatically expire after a specified duration. This is useful for temporary intercepts that should clean up after themselves — for example, test-scoped intercepts or time-limited monitoring.

### Creating Intercepts with a TTL

Pass a `ttlSeconds` parameter to `createIntercept()`. The method returns an `InterceptLease` handle for managing the lease:

```java
// Create intercept with 5-minute TTL
InterceptLease lease = directory.createIntercept(intercept, 300);
```

When the TTL expires, etcd automatically deletes the intercept key. The peer's `InterceptInformer` detects the deletion via its watch, relays it to the `InterceptMatcher`, and the intercept is unregistered — no more callbacks will fire.

Without a TTL (or with `ttlSeconds = 0`), the intercept is attached to the owning peer's lease and lives as long as the peer is running:

```java
// No dedicated TTL — lives as long as the peer
InterceptLease lease = directory.createIntercept(intercept);
// lease == InterceptLease.NONE (a no-op sentinel)
```

### Manual Lease Refresh

Call `keepAlive()` to send a single keep-alive to etcd, extending the lease by its original TTL:

```java
InterceptLease lease = directory.createIntercept(intercept, 30); // 30-second TTL

// ... some time later, before TTL expires ...
lease.keepAlive(); // extends by another 30 seconds
```

This is useful when you want explicit control over when the lease is refreshed — for example, refreshing only when a condition is met.

### Auto-Refresh

For intercepts that should stay alive indefinitely (but still have a TTL as a safety net), use `startAutoRefresh()`. This schedules periodic keep-alive calls at `ttlSeconds / 3` intervals:

```java
InterceptLease lease = directory.createIntercept(intercept, 300); // 5-minute TTL
lease.startAutoRefresh(); // refreshes every ~100 seconds

// ... intercept stays alive until you stop it ...

lease.stopAutoRefresh(); // stop refreshing (lease will expire after TTL)
```

`stopAutoRefresh()` cancels the periodic refresh but does **not** revoke the lease — the intercept remains active until the remaining TTL expires.

### Revoking a Lease (Immediate Removal)

Call `close()` to immediately revoke the lease and remove the intercept:

```java
lease.close(); // intercept removed immediately
```

`close()` is idempotent — calling it multiple times is safe.

### Resource Management with try-with-resources

`InterceptLease` implements `AutoCloseable`, so you can use try-with-resources for automatic cleanup:

```java
try (InterceptLease lease = directory.createIntercept(intercept, 300)) {
    lease.startAutoRefresh();

    // ... intercept is active for the duration of this block ...

} // lease.close() called automatically — intercept removed
```

### Behavior When TTL Expires

When a TTL expires without being refreshed:

1. etcd deletes the intercept key
2. The peer's `InterceptInformer` receives a DELETE watch event
3. `InterceptMatcher` unregisters the intercept
4. No further callbacks fire for the expired intercept
5. The corresponding `InterceptLease` entry is removed from `PalDirectory`

This is the same deletion path used for manual removal — the system does not distinguish between TTL expiry and explicit deletion.

## Managing Intercepts

### List Active Intercepts

List intercepts via the CLI or Java API:

```bash
# List all intercepts
pal intercept ls -d localhost:2379

# List with details (includes TTL column)
pal intercept ls -d localhost:2379 -l
```

Or query programmatically:

```java
List<InterceptRequest> intercepts = directory.listIntercepts();
```

### Remove Intercept

```java
directory.removeIntercept(interceptUuid);
```

### Update Intercept

Remove the old one and create a new one:

```java
directory.removeIntercept(oldInterceptUuid);
directory.createIntercept(newInterceptRequest);
```

## Performance Impact

### Overhead

- **Woven method calls**: ~10-50ns per call (always paid)
- **Pattern matching**: ~100-500ns per call (if intercepts registered)
- **BEFORE callback**: Full RPC roundtrip (~100μs-1ms)
- **AFTER callback**: Message send only (~10-100μs, async)

### Optimization Tips

1. **Use specific patterns**: Narrow patterns match fewer calls
2. **Remove unused intercepts**: Reduces matching overhead
3. **Use AFTER for monitoring**: Doesn't block the caller
4. **Batch callbacks**: Register one intercept for multiple methods

## Debugging Interception

### Intercept Not Firing

**Check 1**: Is application woven?
```bash
javap -c MyClass.class | grep aspectOf
```
Should see AspectJ calls. If not, rebuild with AspectJ plugin.

**Check 2**: Is peer interceptable?
```bash
pal peer ls -d localhost:2379 -l
```
Look for intercept support indicator.

**Check 3**: Does pattern match?
```bash
# Enable debug logging in peer
<logger name="io.quasient.pal.core.InterceptMatcher" level="DEBUG"/>
```

### Callback Peer Not Receiving

**Check 1**: Is callback peer running?
```bash
pal peer ls -d localhost:2379 | grep callback-peer
```

**Check 2**: Is callback peer's RPC endpoint correct?
```bash
pal peer ls -d localhost:2379 -l
```
Verify RPC endpoint is accessible.

## Exception Propagation

When callback handlers throw exceptions, PAL provides policies to control whether those exceptions propagate to the intercepted code.

### Exception Propagation Policies

**PROPAGATE_CONTROLLED_ONLY** (default) - Only propagate exceptions that are explicitly set via `ctx.setExceptionToThrow()` and the callback completes successfully:

```java
// This will propagate
ctx.setExceptionToThrow(new SecurityException("Access denied"));
return new InterceptCallbackResponse();

// This will NOT propagate (callback crashed)
throw new RuntimeException("Callback bug");
```

**Use when**: You want callbacks to signal errors explicitly while protecting against callback bugs.

**PROPAGATE_EXPLICIT_ONLY** - Only propagate exceptions explicitly set via `ctx.setExceptionToThrow()`:

```java
// Same behavior as PROPAGATE_CONTROLLED_ONLY but stricter
// Even if callback completes normally, only explicit exceptions propagate
```

**Use when**: You need fine-grained control over which exceptions propagate.

**PROPAGATE_ALL** - Propagate all exceptions, including callback crashes:

```java
// Both of these will propagate
ctx.setExceptionToThrow(new SecurityException("Access denied"));
// AND
throw new RuntimeException("Callback bug");
```

**Use when**: Testing or development where you want to catch callback bugs immediately.

**SWALLOW_ALL** - Never propagate exceptions (all are logged but swallowed):

```java
// Neither of these will propagate
ctx.setExceptionToThrow(new SecurityException("Access denied"));
throw new RuntimeException("Callback bug");
```

**Use when**: Non-critical monitoring or logging where callback failures shouldn't affect application behavior.

### Checked Exception Policies

Java's checked exception system requires methods to declare which checked exceptions they throw. When a callback tries to throw a checked exception not declared by the intercepted method, PAL applies a policy:

**WRAP** (default) - Wrap undeclared checked exceptions in RuntimeException:

```java
// Method signature: String readFile() throws IOException
// Callback throws: SQLException (not declared)
// Result: RuntimeException wrapping SQLException
```

**REJECT** - Throw `InvalidCallbackExceptionException` to signal the violation:

```java
// Throws InvalidCallbackExceptionException with details about the mismatch
```

**Use when**: Testing to catch incorrect exception types early.

**ALLOW_ALL** - Allow any exception to propagate (bypasses Java type safety):

```java
// SQLException propagates directly (may cause UndeclaredThrowableException)
```

**Warning**: Can violate Java's exception contract. Use only if you understand the risks.

### Configuring Exception Policies

Exception policies can be configured at three levels (most specific wins):

#### Global Default

```bash
# Via CLI flags
pal run --exception-policy PROPAGATE_ALL \
        --checked-exception-policy REJECT \
        -cp app.jar

# Via environment variables
export EXCEPTION_POLICY=PROPAGATE_ALL
export CHECKED_EXCEPTION_POLICY=REJECT

# Via system properties
-Dpal.intercept.exception-policy.default=PROPAGATE_ALL
-Dpal.intercept.checked-exception-policy.default=REJECT
```

#### Per-Type Default

```bash
# Different policies for different intercept types
-Dpal.intercept.exception-policy.before=PROPAGATE_ALL
-Dpal.intercept.exception-policy.after=SWALLOW_ALL
-Dpal.intercept.exception-policy.around=PROPAGATE_CONTROLLED_ONLY
```

#### Per-Intercept Override

```java
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.SecuredService",
    AuthCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false,
    ExceptionPropagationPolicy.PROPAGATE_ALL,
    CheckedExceptionPolicy.REJECT);
```

### Exception Handling Examples

#### Example 1: Validation with Explicit Exceptions

```java
public class ValidationCallback {
    public static InterceptCallbackResponse handle(InterceptContext ctx) {
        String input = (String) ctx.getArgs()[0];
        if (input == null || input.isEmpty()) {
            // This will propagate with PROPAGATE_CONTROLLED_ONLY
            ctx.setExceptionToThrow(new IllegalArgumentException("Input required"));
        }
        return new InterceptCallbackResponse();
    }
}

// Register with default policy (PROPAGATE_CONTROLLED_ONLY)
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    ValidationCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()));
```

#### Example 2: Resilient Monitoring

```java
public class MetricsCallback {
    public static InterceptCallbackResponse handle(InterceptContext ctx) {
        // Even if metrics system crashes, don't break application
        metrics.record(ctx.getMethod(), ctx.getReturnValue());
        return new InterceptCallbackResponse();
    }
}

// Register with SWALLOW_ALL policy
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.AFTER,
    "com.example.Service",
    MetricsCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false,
    ExceptionPropagationPolicy.SWALLOW_ALL,
    null);
```

#### Example 3: Exception Transformation in AROUND

```java
public class ExceptionWrapperCallback {
    public static InterceptCallbackResponse handle(InterceptContext ctx) {
        ProceedResult result = ctx.proceed();

        if (result.hasException()) {
            Throwable original = result.getException();
            // Wrap low-level exceptions in domain exceptions
            ctx.setExceptionToThrow(
                new ServiceException("Operation failed", original)
            );
        }

        return new InterceptCallbackResponse();
    }
}
```

### API Misuse Exceptions

PAL throws specific exceptions when callback code violates the intercept API contract. These always propagate regardless of policy:

**InterceptTypeNotSupportedException** - Operation not supported for current intercept type:

```java
// ERROR: Can't get return value in BEFORE intercept
public static InterceptCallbackResponse handle(InterceptContext ctx) {
    Object value = ctx.getReturnValue();  // throws InterceptTypeNotSupportedException
    return new InterceptCallbackResponse();
}
```

**InterceptPhaseViolationException** - Operation called during wrong phase (AROUND intercepts):

```java
// ERROR: Can't modify arguments after proceed
public static InterceptCallbackResponse handle(InterceptContext ctx) {
    ctx.proceed();
    ctx.setArg(0, "too late");  // throws InterceptPhaseViolationException
    return new InterceptCallbackResponse();
}
```

**InvalidCallbackExceptionException** - Callback threw checked exception not compatible with method signature (only when policy is REJECT):

```java
// Method declares: throws IOException
// Callback throws: SQLException (not compatible)
// Result: InvalidCallbackExceptionException with details
```

These exceptions help you catch programming errors during development. Fix the callback code to follow the API contract.

### Async Intercepts and Exceptions

**BEFORE_ASYNC** and **AFTER_ASYNC** intercepts always use `SWALLOW_ALL` policy:

```java
// Fire-and-forget - exceptions logged but never propagate
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE_ASYNC,
    "com.example.Service",
    AsyncCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false,
    ExceptionPropagationPolicy.PROPAGATE_ALL,  // ignored for async
    null);
```

This is because async intercepts don't block the caller, so there's no synchronous path to propagate exceptions.

## Intercept Bundles

When working with multiple intercepts, defining them individually can become tedious and error-prone. **Intercept bundles** let you declare a group of related intercepts and manage them as a unit --- either via YAML files on the CLI, or programmatically from Java code.

### What is a Bundle?

A bundle is a named collection of intercept definitions with shared defaults. It provides:

- **Declarative or programmatic** --- define bundles in YAML files or build them with the Java builder API
- **Shared defaults** --- set peer, priority, TTL, and exception policies once, override per-intercept as needed
- **Lifecycle management** --- apply, diff, status-check, and remove all intercepts in one operation
- **Idempotency** --- re-applying a bundle skips intercepts that already exist
- **Metadata tracking** --- PAL stores bundle metadata in etcd so you can query or remove by bundle name

### Bundle YAML Format

```yaml
bundle: "fraud-check-v1"
defaults:
  peer: "fraud-checker"
  priority: 0
  ttl: 30s

intercepts:
  - target: com.acme.payment.OrderService.placeOrder
    type: BEFORE
    callback:
      class: com.acme.fraud.FraudChecker
      method: verify
    params:
      - com.acme.payment.Order

  - target: com.acme.payment.OrderService.refund
    type: AROUND
    callback:
      class: com.acme.fraud.FraudChecker
      method: wrapRefund
    priority: 10

  - target: com.acme.payment.OrderService.status
    kind: field
    fieldOp: GET
    type: AFTER
    callback:
      class: com.acme.audit.FieldAuditor
      method: onFieldRead
```

Each entry under `intercepts` uses the `target` field in `ClassName.memberName` format. The `defaults` section sets values inherited by all intercepts unless individually overridden.

The optional `params` field restricts matching to a specific method overload. When `params` is omitted, the intercept matches all overloads of the target method. Parameter types must be fully qualified (e.g., `com.acme.payment.Order`, not `Order`).

### Bundle Commands

| Command | Description |
|---------|-------------|
| `pal intercept apply <file>` | Create intercepts from a YAML bundle |
| `pal intercept apply --dry-run <file>` | Preview what would change without applying |
| `pal intercept diff <file>` | Compare bundle against current directory state |
| `pal intercept status -f <file>` | Check which bundle intercepts are active |
| `pal intercept status --bundle <name>` | Check status by stored bundle name |
| `pal intercept rm -f <file>` | Remove all intercepts defined in a bundle file |
| `pal intercept rm --bundle <name>` | Remove all intercepts by bundle name |
| `pal intercept rm --peer <name>` | Remove all intercepts for a peer |

### Typical Workflow

```bash
# 1. Preview changes
pal intercept diff -d localhost:2379 fraud-check.yaml

# 2. Apply the bundle
pal intercept apply -d localhost:2379 fraud-check.yaml

# 3. Verify the intercepts are active
pal intercept status -d localhost:2379 -f fraud-check.yaml

# 4. When done, remove all intercepts
pal intercept rm -d localhost:2379 -f fraud-check.yaml
```

### Idempotent Apply

Running `pal intercept apply` on a bundle that has already been applied is safe --- existing intercepts are detected and skipped:

```
Applied: 0 created, 3 skipped, 0 failed
```

This makes bundles suitable for use in scripts and CI/CD pipelines.

### Bundle Metadata

When a bundle is applied, PAL stores lightweight metadata in etcd recording the bundle name, the peer UUID, and the UUIDs of the intercepts that were created. This metadata enables:

- **`pal intercept rm --bundle <name>`** --- remove all intercepts by bundle name without needing the original YAML file
- **`pal intercept status --bundle <name>`** --- check the status of a previously applied bundle

For full CLI reference details, see the [CLI Reference](../cli-reference.md#pal-intercept-apply---apply-intercept-bundle).

### Programmatic API

Bundles can also be built and managed entirely from Java code using the builder API. This is useful when intercepts are driven by application logic rather than static YAML files.

**Building and applying a bundle:**

```java
// Define bundle-level defaults (peer name, priority, TTL, etc.)
InterceptBundleDefaults defaults =
    new InterceptBundleDefaults("fraud-checker", null, null, null, null, null, null);

// Build the bundle with the fluent builder API
InterceptBundleSpec bundle = InterceptBundleSpec.builder("fraud-check-v1")
    .defaults(defaults)
    .addIntercept(InterceptSpec.builder()
        .targetClass("com.acme.OrderService")
        .targetName("placeOrder")
        .type(InterceptType.BEFORE)
        .callbackClass("com.acme.FraudChecker")
        .callbackMethod("verify")
        .parameterTypes(List.of("com.acme.payment.Order"))  // specific overload
        .build())
    .addIntercept(InterceptSpec.builder()
        .targetClass("com.acme.OrderService")
        .targetName("refund")
        .type(InterceptType.AROUND)
        .callbackClass("com.acme.FraudChecker")
        .callbackMethod("wrapRefund")
        .build())
    .addIntercept(InterceptSpec.builder()
        .targetClass("com.acme.OrderService")
        .targetName("status")
        .kind(InterceptableKind.FIELD)
        .fieldOpType(FieldOpType.GET)
        .type(InterceptType.AFTER)
        .callbackClass("com.acme.FieldAuditor")
        .callbackMethod("onFieldRead")
        .build())
    .build();

// Apply the bundle
InterceptManager manager = new InterceptManager(palDirectory);
ApplyResult result = manager.apply(bundle);
// result.getCreatedCount() == 3
```

**Checking status and removing:**

```java
// Check which intercepts are active
BundleStatus status = manager.status(bundle);
// status.getActiveCount() / status.getTotalCount()

// Diff against current directory state
List<InterceptDiff> diffs = manager.diff(bundle);
// Each entry is CREATE, UNCHANGED, or MODIFIED

// Remove all intercepts in the bundle
RemoveResult removed = manager.remove(bundle);

// Or remove by bundle name (without the original spec)
RemoveResult removed = manager.removeByBundle("fraud-check-v1");
```

The programmatic API uses the same `InterceptManager` as the CLI commands. Applying a bundle is idempotent regardless of whether it was applied via YAML or the builder API.

## Callback Timeouts

By default, the intercepted peer waits 3000ms for a callback peer to respond to synchronous BEFORE/AFTER callbacks. This can be configured at two levels:

**Global default** via `pal run --callback-timeout-ms <ms>` (or env var `PAL_CALLBACK_TIMEOUT_MS`):

- `--callback-timeout-ms 3000` — wait up to 3 seconds (default)
- `--callback-timeout-ms 0` — no timeout (infinite wait)

**Per-intercept override** via the `callbackTimeout` field in intercept bundles:
```yaml
defaults:
  callbackTimeout: "5s"

intercepts:
  - target: "com.example.Calculator#add"
    type: BEFORE
    callbackTimeout: "500ms"  # overrides default for this intercept
```

Supported duration units: `ms`, `s`, `m`, `h`, `d`.

Timeout resolution order: per-intercept override → bundle defaults → global peer setting.

When a callback times out, the intercepted peer logs a warning and proceeds as if the callback returned `shouldProceed=true` with no mutations.

## Limitations

### Only Woven Code

Interception only works for code compiled with AspectJ weaving. Standard JDK classes (like `java.lang.String`) cannot be intercepted.

### Pattern Syntax

Uses ant-style patterns, not regex:

- `*` matches one level
- `**` matches multiple levels
- No regex features like `(a|b)` or `[0-9]`

## Security Considerations

Anyone with directory access can register intercepts on any peer. To restrict:

1. Use network security (firewall etcd access)
2. Implement authorization in your intercept handler
3. Don't run untrusted code with `--interceptable`

## Further Reading

- [Peers and Logs](peers-and-logs.md) - Understanding peers
- [RPC](rpc.md) - How callbacks are delivered
- [CLI Reference: Intercept Commands](../cli-reference.md#pal-intercept-apply---apply-intercept-bundle) - Full reference for bundle CLI commands
- [Writing Callback Handlers](../guides/writing-callback-handlers.md) - Implementing callback logic with practical examples
- [Testing Guide](../guides/testing-with-interception.md) - Practical testing patterns
- [Local Development](../guides/local-development.md) - Setting up for interception
