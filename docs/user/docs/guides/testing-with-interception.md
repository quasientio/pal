# Testing with Interception

PAL's interception system is powerful for testing - you can verify method calls, inspect arguments, check state, and mock return values without modifying application code.

## Why Use Interception for Testing?

Traditional testing requires:

- Test doubles (mocks, stubs)
- Dependency injection
- Modifying code for testability

With PAL interception:

- Test real code as-is
- Verify actual method calls
- Inspect internal state
- No code changes needed

## Basic Testing Pattern

### 1. Application Under Test

```java
package com.example.app;

public class UserService {
    private DatabaseService db;

    public User createUser(String name, String email) {
        // Validate
        if (name == null || email == null) {
            throw new IllegalArgumentException("Name and email required");
        }

        // Save to database
        User user = new User(name, email);
        db.save(user);

        // Send welcome email
        EmailService.sendWelcome(user);

        return user;
    }
}
```

### 2. Test with Interception

```java
package com.example.app;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserServiceTest {

    private UUID testPeerUuid;
    private UUID callbackPeerUuid;
    private PalDirectory directory;
    private List<ExecMessage> receivedCallbacks;

    @Before
    public void setUp() throws Exception {
        // Start etcd for testing
        directory = new PalDirectory("localhost:2379");

        // Start application under test
        testPeerUuid = startApplicationPeer();

        // Start callback peer to receive intercepts
        callbackPeerUuid = startCallbackPeer();

        receivedCallbacks = new CopyOnWriteArrayList<>();
    }

    @Test
    public void testCreateUserSavesToDatabase() {
        // Register intercept for database save
        InterceptRequest intercept = InterceptRequest.builder()
            .classPattern("com.example.app.DatabaseService")
            .methodPattern("save")
            .interceptType(InterceptType.BEFORE)
            .callbackPeer(callbackPeerUuid)
            .build();
        directory.createIntercept(intercept);

        // Call application
        callUserService("createUser", "John", "john@example.com");

        // Wait for callback
        Thread.sleep(500);

        // Verify database save was called
        assertEquals(1, receivedCallbacks.size());
        ExecMessage callback = receivedCallbacks.get(0);
        assertEquals("save", callback.getMethod());
        assertEquals("com.example.app.DatabaseService", callback.getSourceClass());

        // Verify argument is User object
        Object[] args = callback.getArgs();
        assertEquals(1, args.length);
        assertTrue(args[0] instanceof User);
    }

    @Test
    public void testCreateUserSendsWelcomeEmail() {
        // Register intercept for email service
        InterceptRequest intercept = InterceptRequest.builder()
            .classPattern("com.example.app.EmailService")
            .methodPattern("sendWelcome")
            .interceptType(InterceptType.BEFORE)
            .callbackPeer(callbackPeerUuid)
            .build();
        directory.createIntercept(intercept);

        // Call application
        callUserService("createUser", "Jane", "jane@example.com");

        // Wait for callback
        Thread.sleep(500);

        // Verify email was sent
        assertEquals(1, receivedCallbacks.size());
        ExecMessage callback = receivedCallbacks.get(0);
        assertEquals("sendWelcome", callback.getMethod());
    }

    @After
    public void tearDown() {
        stopPeer(testPeerUuid);
        stopPeer(callbackPeerUuid);
        receivedCallbacks.clear();
    }

    private UUID startApplicationPeer() {
        // Start peer with interceptable flag
        ProcessBuilder pb = new ProcessBuilder(
            "pal", "run",
            "-d", "localhost:2379",
            "--interceptable",
            "--rpc", "auto",
            "-cp", "target/app.jar",
            "com.example.app.Main"
        );
        Process process = pb.start();

        // Wait for peer to register
        Thread.sleep(2000);

        // Get peer UUID from directory
        return findPeerByName("app");
    }

    private UUID startCallbackPeer() {
        // Callback peer receives intercept callbacks
        // (Implementation details...)
    }

    private void callUserService(String method, Object... args) {
        // Use pal call or ThinPeer to invoke application
    }
}
```

## Testing Patterns

### Pattern 1: Verify Method Called

```java
@Test
public void testMethodWasCalled() {
    // Setup intercept
    InterceptRequest intercept = InterceptRequest.builder()
        .classPattern("com.example.Service")
        .methodPattern("process")
        .interceptType(InterceptType.BEFORE)
        .callbackPeer(callbackPeerUuid)
        .build();
    directory.createIntercept(intercept);

    // Execute
    triggerApplication();

    // Verify
    assertTrue("Expected method to be called",
               callbacksReceived.stream()
                   .anyMatch(cb -> cb.getMethod().equals("process")));
}
```

### Pattern 2: Verify Arguments

```java
@Test
public void testMethodCalledWithCorrectArguments() {
    InterceptRequest intercept = createIntercept(
        "com.example.Service", "processOrder");

    // Execute
    createOrder("ORDER-123", 99.99);

    // Verify arguments
    ExecMessage callback = getCallback("processOrder");
    Object[] args = callback.getArgs();
    assertEquals("ORDER-123", args[0]);
    assertEquals(99.99, args[1]);
}
```

### Pattern 3: Verify Call Order

```java
@Test
public void testOperationsHappenInOrder() {
    // Intercept multiple methods
    createIntercept("com.example.Service", "validate");
    createIntercept("com.example.Service", "save");
    createIntercept("com.example.Service", "notify");

    // Execute
    processTransaction();

    // Verify order
    List<String> callOrder = callbacksReceived.stream()
        .map(ExecMessage::getMethod)
        .collect(Collectors.toList());

    assertEquals(Arrays.asList("validate", "save", "notify"),
                 callOrder);
}
```

### Pattern 4: Verify Never Called

```java
@Test
public void testErrorPathDoesNotSendEmail() {
    // Intercept email sending
    createIntercept("com.example.EmailService", "send");

    // Execute error path
    try {
        processInvalidOrder();
    } catch (Exception e) {
        // Expected
    }

    // Verify email was NOT sent
    assertTrue("Email should not be sent on error",
               callbacksReceived.isEmpty());
}
```

### Pattern 5: Mock Return Values (AROUND)

```java
@Test
public void testWithMockedDatabase() {
    // Mock database to return test data
    InterceptRequest intercept = InterceptRequest.builder()
        .classPattern("com.example.DatabaseService")
        .methodPattern("findUser")
        .interceptType(InterceptType.AROUND)
        .callbackPeer(callbackPeerUuid)
        .build();
    directory.createIntercept(intercept);

    // Configure callback peer to return mock data
    callbackPeer.setMockResponse("findUser",
                                  new User("Test", "test@example.com"));

    // Execute - will use mock data instead of real database
    User result = getUser("123");

    // Verify
    assertEquals("Test", result.getName());
    // Database was never actually called
}
```

### Pattern 6: State Verification

```java
@Test
public void testInternalStateAfterOperation() {
    // Intercept to inspect state
    InterceptRequest intercept = InterceptRequest.builder()
        .classPattern("com.example.ShoppingCart")
        .methodPattern("addItem")
        .interceptType(InterceptType.AFTER)
        .callbackPeer(callbackPeerUuid)
        .build();
    directory.createIntercept(intercept);

    // Execute
    addItemToCart("ITEM-123");

    // Callback can inspect cart's internal state
    ExecMessage callback = getCallback("addItem");

    // Get cart instance from callback and verify
    // (Using ObjectRef to access remote object)
}
```

## Integration with JUnit

### Base Test Class

```java
public abstract class PalIntegrationTest {

    protected PalDirectory directory;
    protected UUID appPeerUuid;
    protected UUID callbackPeerUuid;
    protected List<ExecMessage> callbacks;

    @BeforeClass
    public static void setUpClass() {
        // Ensure etcd is running
        checkEtcdRunning();
    }

    @Before
    public void setUp() throws Exception {
        directory = new PalDirectory("localhost:2379");
        callbacks = new CopyOnWriteArrayList<>();

        // Start application and callback peers
        appPeerUuid = startApplicationPeer();
        callbackPeerUuid = startCallbackPeer();
    }

    @After
    public void tearDown() {
        // Cleanup
        stopAllPeers();
        removeAllIntercepts();
        callbacks.clear();
    }

    protected void createIntercept(String className, String methodName) {
        createIntercept(className, methodName, InterceptType.BEFORE);
    }

    protected void createIntercept(String className, String methodName,
                                   InterceptType type) {
        InterceptRequest intercept = InterceptRequest.builder()
            .classPattern(className)
            .methodPattern(methodName)
            .interceptType(type)
            .callbackPeer(callbackPeerUuid)
            .build();
        directory.createIntercept(intercept);
    }

    protected ExecMessage getCallback(String methodName) {
        return callbacks.stream()
            .filter(cb -> cb.getMethod().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No callback received for method: " + methodName));
    }

    protected void waitForCallbacks(int count) throws InterruptedException {
        int attempts = 0;
        while (callbacks.size() < count && attempts++ < 50) {
            Thread.sleep(100);
        }
        if (callbacks.size() < count) {
            throw new AssertionError(
                "Expected " + count + " callbacks, got " + callbacks.size());
        }
    }
}
```

### Test Using Base Class

```java
public class OrderServiceTest extends PalIntegrationTest {

    @Test
    public void testOrderProcessing() throws Exception {
        // Setup intercepts
        createIntercept("com.example.OrderService", "validate");
        createIntercept("com.example.OrderService", "charge");
        createIntercept("com.example.OrderService", "ship");

        // Execute
        processOrder("ORDER-123");

        // Wait for all callbacks
        waitForCallbacks(3);

        // Verify
        assertEquals(3, callbacks.size());
        List<String> methods = callbacks.stream()
            .map(ExecMessage::getMethod)
            .collect(Collectors.toList());
        assertTrue(methods.contains("validate"));
        assertTrue(methods.contains("charge"));
        assertTrue(methods.contains("ship"));
    }
}
```

## Performance Testing

```java
@Test
public void testPerformanceUnderLoad() {
    // Intercept to measure timing
    createIntercept("com.example.Service", "process", InterceptType.AFTER);

    // Execute 1000 operations
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
        processRequest(i);
    }
    long duration = System.currentTimeMillis() - startTime;

    // Verify all completed
    waitForCallbacks(1000);

    // Analyze performance
    double avgLatency = duration / 1000.0;
    System.out.println("Average latency: " + avgLatency + "ms");
    assertTrue("Latency too high", avgLatency < 10.0);
}
```

## Best Practices

### 1. Clean Up After Tests

Always remove intercepts and stop peers:

```java
@After
public void tearDown() {
    directory.listIntercepts().forEach(i ->
        directory.removeIntercept(i.getUuid()));
    stopAllPeers();
}
```

### 2. Use Specific Patterns

Narrow patterns reduce noise:

```java
// Good
.classPattern("com.example.OrderService")
.methodPattern("processOrder")

// Avoid (too broad)
.classPattern("com.example.**.*")
.methodPattern("*")
```

### 3. Wait for Callbacks

Intercepts are asynchronous:

```java
// Always wait
waitForCallbacks(expectedCount);

// Or with timeout
boolean received = waitForCallbacks(expectedCount, 5000);
assertTrue("Callbacks not received in time", received);
```

### 4. Use Chronicle for Tests

Faster than Kafka:

```bash
pal run --wal file:/tmp/test-wal --interceptable \
  -cp app.jar com.example.App
```

### 5. Isolate Tests

Each test should:

- Start fresh peers
- Register only needed intercepts
- Clean up completely

### 6. Test Constructors and Field Operations

In-flight tracking applies to all operation types -- methods, constructors, and field accesses. When testing intercepted constructors or fields, the same activation safety guarantees apply:

```java
// Intercept a constructor
InterceptRequest ctorIntercept = InterceptRequest.builder()
    .classPattern("com.example.UserService")
    .methodPattern("new")
    .interceptType(InterceptType.BEFORE)
    .callbackPeer(callbackPeerUuid)
    .build();

// Intercept a field access
InterceptRequest fieldIntercept = InterceptRequest.builder()
    .classPattern("com.example.Config")
    .fieldPattern("maxRetries")
    .interceptType(InterceptType.AROUND)
    .callbackPeer(callbackPeerUuid)
    .build();
```

Parameter types are also considered: intercepting `process(String)` does not affect `process(String, int)`. This means tests for overloaded methods can safely target specific signatures without interfering with other overloads.

## Debugging Tests

### Enable Debug Logging

```xml
<logger name="io.quasient.pal.core.InterceptMatcher" level="DEBUG"/>
```

### Print All Callbacks

```java
@After
public void printCallbacks() {
    System.out.println("Received " + callbacks.size() + " callbacks:");
    callbacks.forEach(cb ->
        System.out.println("  " + cb.getMethod() + "(" +
                          Arrays.toString(cb.getArgs()) + ")"));
}
```

### Verify Weaving

```bash
javap -c target/classes/com/example/MyClass.class | grep aspectOf
```

Should show AspectJ calls.

## Example: Complete Test Suite

See the PAL integration tests for complete examples:

- `modules/itt/src/test/java/io/quasient/pal/core/InterceptTests.java`
- `modules/itt/src/test/java/io/quasient/pal/core/BinaryRpcTest.java`

## Further Reading

- [Concepts: Interception](../concepts/interception.md) - Interception fundamentals
- [Concepts: RPC](../concepts/rpc.md) - How callbacks are delivered
- [Local Development Guide](local-development.md) - Setting up test environment
- [Distributed Application Guide](distributed-application.md) - Integration testing
