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

Callback executes **after** the method, asynchronously:

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
- Log method exit
- Collect metrics
- Audit trail

**Timing**: Target method completes first, then callback is sent (non-blocking).

### AROUND

Callback executes **instead** of the method (can skip execution):

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
- Circuit breaker pattern
- Replace functionality

**Timing**: Callback decides whether to execute original method.

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
                <groupId>com.quasient.pal</groupId>
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
<logger name="com.quasient.pal.core.InterceptMatcher" level="DEBUG"/>
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
