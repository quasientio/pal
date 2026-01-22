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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.Calculator")
    .methodPattern("add")
    .interceptType(InterceptType.BEFORE)
    .callbackPeer(myPeerUuid)
    .build();
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.Calculator")
    .methodPattern("add")
    .interceptType(InterceptType.AFTER)
    .callbackPeer(myPeerUuid)
    .build();
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.Calculator")
    .methodPattern("add")
    .interceptType(InterceptType.AROUND)
    .callbackPeer(myPeerUuid)
    .build();
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.Service")
    .methodPattern("process")
    .interceptType(InterceptType.BEFORE_ASYNC)  // or AFTER_ASYNC
    .callbackPeer(myPeerUuid)
    .build();
```

**Use cases**:

- Telemetry and monitoring
- Audit logging
- Metrics collection
- Notifications

**Timing**: Callback is sent but caller doesn't wait for response.

**Limitations**: Cannot modify arguments, return values, or throw exceptions (fire-and-forget).

## Multiple Intercepts and Ordering

When multiple intercepts match the same operation, they execute in a specific order.

### Execution Order

1. **Local vs Remote**: Local intercepts (callback peer = intercepted peer) always execute **before** remote intercepts
2. **Registration order**: Within each category, intercepts execute in the order they were registered

```
BEFORE phase:
  1. Local BEFORE callbacks
  2. Remote BEFORE callbacks

AROUND phase (onion model):
  3. Local AROUND callbacks (outermost layers)
  4. Remote AROUND callbacks (inner layers)
  [Method Execution - innermost]
  (Return values propagate outward)

AFTER phase:
  5. Local AFTER callbacks
  6. Remote AFTER callbacks
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.Service")
    .methodPattern("processRequest")
    .interceptType(InterceptType.BEFORE)
    .callbackPeer(myCallbackPeerUuid)
    .build();

// 3. Register
directory.createIntercept(intercept);

// 4. Callbacks will now be sent to your peer
```

### Pattern Matching

Use ant-style patterns to match classes and methods:

#### Exact Match
```java
.classPattern("com.example.Calculator")
.methodPattern("add")
```
Matches only `Calculator.add()`.

#### Wildcard
```java
.classPattern("com.example.*")
.methodPattern("process*")
```
Matches all classes in `com.example` package with methods starting with "process".

#### Recursive
```java
.classPattern("com.example.**.*")
.methodPattern("*")
```
Matches all classes in `com.example` and subpackages, all methods.

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
pal run -d localhost:2379 --rpc auto -n callback-peer \
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
    InterceptRequest intercept = InterceptRequest.builder()
        .classPattern("com.example.Service")
        .methodPattern("processRequest")
        .interceptType(InterceptType.BEFORE)
        .callbackPeer(callbackPeerUuid)
        .build();
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
InterceptRequest beforeIntercept = InterceptRequest.builder()
    .classPattern("com.example.Service")
    .methodPattern("*")
    .interceptType(InterceptType.BEFORE)
    .callbackPeer(monitorPeerUuid)
    .build();

InterceptRequest afterIntercept = InterceptRequest.builder()
    .classPattern("com.example.Service")
    .methodPattern("*")
    .interceptType(InterceptType.AFTER)
    .callbackPeer(monitorPeerUuid)
    .build();

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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.**.*")
    .methodPattern("*")
    .interceptType(InterceptType.BEFORE)
    .callbackPeer(auditPeerUuid)
    .build();

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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.DatabaseService")
    .methodPattern("queryDatabase")
    .interceptType(InterceptType.AROUND)
    .callbackPeer(mockPeerUuid)
    .build();

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

## Managing Intercepts

### List Active Intercepts

```bash
pal ls -d localhost:2379 -I
```

Shows all registered intercepts.

### Remove Intercept

```java
directory.removeIntercept(interceptUuid);
```

Or from CLI:
```bash
pal rm -d localhost:2379 -I intercept-uuid
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
pal ls -d localhost:2379 -P -l
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
pal ls -d localhost:2379 -P | grep callback-peer
```

**Check 2**: Is callback peer's RPC endpoint correct?
```bash
pal ls -d localhost:2379 -P -l
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
InterceptRequest.builder()
    .interceptType(InterceptType.BEFORE)
    .callbackClass(AuthCallback.class.getName())
    .exceptionPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_ALL)
    .checkedExceptionPolicy(CheckedExceptionPolicy.REJECT)
    .build();
```

### Exception Handling Examples

#### Example 1: Validation with Explicit Exceptions

```java
public class ValidationCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        String input = (String) ctx.getArgs()[0];
        if (input == null || input.isEmpty()) {
            // This will propagate with PROPAGATE_CONTROLLED_ONLY
            ctx.setExceptionToThrow(new IllegalArgumentException("Input required"));
        }
        return new InterceptCallbackResponse();
    }
}

// Register with default policy (PROPAGATE_CONTROLLED_ONLY)
InterceptRequest.builder()
    .interceptType(InterceptType.BEFORE)
    .callbackClass(ValidationCallback.class.getName())
    .build();
```

#### Example 2: Resilient Monitoring

```java
public class MetricsCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        // Even if metrics system crashes, don't break application
        metrics.record(ctx.getMethod(), ctx.getReturnValue());
        return new InterceptCallbackResponse();
    }
}

// Register with SWALLOW_ALL policy
InterceptRequest.builder()
    .interceptType(InterceptType.AFTER)
    .callbackClass(MetricsCallback.class.getName())
    .exceptionPropagationPolicy(ExceptionPropagationPolicy.SWALLOW_ALL)
    .build();
```

#### Example 3: Exception Transformation in AROUND

```java
public class ExceptionWrapperCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
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
public InterceptCallbackResponse handle(InterceptContext ctx) {
    Object value = ctx.getReturnValue();  // throws InterceptTypeNotSupportedException
    return new InterceptCallbackResponse();
}
```

**InterceptPhaseViolationException** - Operation called during wrong phase (AROUND intercepts):

```java
// ERROR: Can't modify arguments after proceed
public InterceptCallbackResponse handle(InterceptContext ctx) {
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
InterceptRequest.builder()
    .interceptType(InterceptType.BEFORE_ASYNC)
    .callbackClass(AsyncCallback.class.getName())
    .exceptionPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_ALL)  // ignored
    .build();
```

This is because async intercepts don't block the caller, so there's no synchronous path to propagate exceptions.

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
- [Writing Callback Handlers](../guides/writing-callback-handlers.md) - Implementing callback logic with practical examples
- [Testing Guide](../guides/testing-with-interception.md) - Practical testing patterns
- [Local Development](../guides/local-development.md) - Setting up for interception
