# Use Cases

PAL's core abstraction—operations as messages—enables different capabilities for different roles. This page shows how developers, SREs, and architects can leverage PAL for their specific needs.

## For Developers: Testing Without Mocks

### The Problem

Traditional testing requires extensive mocking and stubbing:

```java
@Test
public void testOrderProcessing() {
    // Mock all dependencies
    PaymentService mockPayment = mock(PaymentService.class);
    InventoryService mockInventory = mock(InventoryService.class);
    NotificationService mockNotification = mock(InventoryService.class);

    when(mockPayment.charge(any(), anyDouble())).thenReturn(true);
    when(mockInventory.reserve(any(), anyInt())).thenReturn(true);

    OrderService service = new OrderService(
        mockPayment, mockInventory, mockNotification
    );

    // Test
    service.processOrder(order);

    // Verify
    verify(mockPayment).charge(order.getCard(), order.getTotal());
    verify(mockInventory).reserve(order.getProduct(), order.getQuantity());
}
```

This is tedious, brittle, and doesn't test real code interactions.

### The PAL Solution

Run the real code, but intercept the dependencies:

```java
@Test
public void testOrderProcessing() {
    // Start peer with real services
    PalPeer peer = PalPeer.local()
        .withInterception()
        .withWAL("/tmp/test-wal")
        .start();

    // Register intercepts for dependencies
    peer.intercept("PaymentService.charge")
        .around((args) -> true);  // Always succeed

    peer.intercept("InventoryService.reserve")
        .around((args) -> true);  // Always succeed

    // Run REAL code
    OrderService service = new OrderService();
    service.processOrder(order);

    // Verify via message inspection
    List<ExecMessage> messages = peer.getMessages();
    assertThat(messages).contains(
        message("PaymentService", "charge", order.getCard(), order.getTotal()),
        message("InventoryService", "reserve", order.getProduct(), order.getQuantity())
    );
}
```

**Benefits:**

- No mocking framework needed
- Test real code paths
- See actual method calls in the log
- Faster to write, easier to maintain

### Scenario: Integration Testing

**Challenge:** Testing microservice interactions without deploying everything.

**Solution:**

```bash
# Terminal 1: Run service A (real)
pal run --wal service-a-log --rpc auto -cp service-a.jar Main

# Terminal 2: Run service B with intercepted dependencies
pal run --interceptable --wal service-b-log --rpc auto -cp service-b.jar Main

# Terminal 3: Register intercepts for service B's external calls
# (Intercept calls to Service C, return canned responses)

# Terminal 4: Run tests against Service B
mvn test
```

You're testing real Service B code, but with controlled responses from dependencies.

## For SREs: Production Hot-Patching

### The Problem

A bug is discovered in production:

```java
public double calculateDiscount(Order order) {
    // BUG: Should be 10%, not 1%
    return order.getTotal() * 0.01;
}
```

Traditional fix requires:

1. Fix code (5 minutes)
2. Run tests (10 minutes)
3. Build image (5 minutes)
4. Deploy (10 minutes)
5. Verify (5 minutes)

**Total: 35 minutes of downtime** (or accepting wrong discounts).

### The PAL Solution

Hot-patch in 60 seconds:

```bash
# 1. Register an AROUND intercept that fixes the bug
pal intercept -d localhost:2379 -p order-service \
  --class OrderService --method calculateDiscount \
  --type AROUND --callback-peer fix-peer

# The fix-peer returns correct calculation:
# return order.getTotal() * 0.10
```

**Total: 60 seconds, zero downtime.**

Then deploy the proper fix during the next maintenance window.

### Scenario: Incident Response

**Challenge:** Critical bug causing revenue loss, but root cause unclear.

**Timeline:**

```
T+0:00  Alert: Payment failures spiking
T+0:02  Check logs: Seeing NullPointerException in PaymentService
T+0:05  Enable verbose logging via intercept (BEFORE all PaymentService methods)
T+0:06  See detailed args: Issue is when PaymentMethod is null
T+0:08  Register intercept to reject null PaymentMethod early with clear error
T+0:09  Failures stop, users get clear error message
T+0:30  Proper fix deployed
```

**With traditional approach:**

- T+0:05 would be: redeploy with debug logging enabled
- T+0:10 would be: finally see logs
- T+0:20 would be: deploy fix
- T+0:30 would be: fix live

**PAL saved 20 minutes** of revenue-impacting downtime.

### Scenario: A/B Testing in Production

**Challenge:** Test new algorithm on 10% of traffic.

**Solution:**

```java
// Register intercept that routes to new implementation
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("RecommendationService")
    .methodPattern("getRecommendations")
    .interceptType(InterceptType.AROUND)
    .callbackPeer("experiment-peer")
    .build();

// In experiment-peer:
public List<Product> handleIntercept(Context ctx) {
    if (Math.random() < 0.10) {
        // 10% traffic: use new algorithm
        return newAlgorithm.recommend(ctx.getArgs());
    } else {
        // 90% traffic: continue to original
        return ctx.proceed();
    }
}
```

**No feature flags, no if-statements in production code. Pure message-level routing.**

## For Architects: Event Sourcing & Distributed Systems

### The Problem

Implementing event sourcing requires:

- Explicit event definitions
- Event publishing code throughout application
- Event store integration
- Event replay logic
- State reconstruction

This is intrusive and couples business logic to infrastructure.

### The PAL Solution

Event sourcing happens automatically because operations are logged:

```bash
# Run with WAL
pal run --wal order-service-events -cp app.jar OrderService

# Later: Replay all events to reconstruct state
pal run --source-log order-service-events -cp app.jar
```

**Every operation is an event. No explicit event publishing needed.**

### Scenario: Distributed Order Processing

**Challenge:** Build an order processing system spanning multiple services.

**Traditional approach:**

```
OrderService ──REST──► PaymentService
             ──REST──► InventoryService
             ──REST──► NotificationService
```

Requires: REST endpoints, HTTP clients, error handling, retry logic, circuit breakers.

**PAL approach:**

```bash
# Peer 1: Order Service
pal run -d etcd:2379 -k kafka:9092 \
  --wal order-wal --rpc auto -n order-service \
  -cp order.jar OrderService

# Peer 2: Payment Service
pal run -d etcd:2379 -k kafka:9092 \
  --wal payment-wal --rpc auto -n payment-service \
  -cp payment.jar PaymentService

# Peer 3: Inventory Service
pal run -d etcd:2379 -k kafka:9092 \
  --wal inventory-wal --rpc auto -n inventory-service \
  -cp inventory.jar InventoryService
```

**Application code remains unchanged:**

```java
// OrderService.java - looks like local code
public void processOrder(Order order) {
    boolean charged = paymentService.charge(order.getCard(), order.getTotal());
    boolean reserved = inventoryService.reserve(order.getProduct(), order.getQuantity());

    if (charged && reserved) {
        notificationService.send(order.getEmail(), "Order confirmed");
    }
}
```

PAL handles:

- Service discovery (via etcd)
- RPC (transparent method calls across network)
- Message logging (all operations in WAL)
- Replay (reconstruct state from logs)

**No REST, no HTTP, no explicit RPC code.**

### Scenario: Actor-Based System

**Challenge:** Implement actor model for high-concurrency system.

**Traditional approach (Akka):**

```java
class OrderActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ProcessOrder.class, msg -> {
                // Handle explicitly
            })
            .build();
    }
}

// Send message explicitly
orderActor.tell(new ProcessOrder(order), self());
```

**PAL approach:**

```java
// Normal Java class
class OrderService {
    public void processOrder(Order order) {
        // Business logic
    }
}

// Call normally
orderService.processOrder(order);
```

**PAL converts this to asynchronous message** if configured:

```bash
# Run with async message handling
pal run --wal order-wal --async-dispatch -cp app.jar OrderService
```

Now `orderService.processOrder(order)` executes asynchronously via message passing, but code looks synchronous.

## For Security Teams: Audit & Compliance

### The Problem

Compliance requires audit trail of all operations, especially:

- Who accessed what data
- What changes were made
- When did they happen

Traditional approach: Manual audit logging throughout code.

### The PAL Solution

Every operation is automatically logged:

```bash
# Run with comprehensive logging
pal run --wal audit-log --audit-mode full -cp app.jar

# Later: Query audit log
pal print -l audit-log --filter "class=AccountService" --output-format FULL
```

**Output:**

```
Message 1047:
  Timestamp: 2023-11-09T14:23:10.123Z
  Peer: web-server-3 (uuid: abc-123)
  User: alice@company.com (from context)
  Class: AccountService
  Method: withdraw
  Args: [Account(id=9876), 5000.00]
  Result: Transaction(id=tx-5551, status=SUCCESS)

Message 1048:
  Timestamp: 2023-11-09T14:23:10.456Z
  Peer: web-server-3 (uuid: abc-123)
  User: alice@company.com
  Class: AuditService
  Method: logWithdrawal
  Args: [Account(id=9876), 5000.00, "ATM withdrawal"]
```

**Complete audit trail without a single log statement in business code.**

### Scenario: GDPR Right to Access

**Challenge:** User requests all data you have about them.

**Solution:**

```bash
# Query all operations involving user's email
pal print -l application-log \
  --filter "args contains 'user@example.com'" \
  --from-date "2023-01-01" \
  --to-date "2023-12-31" \
  --output-format JSON > user-data.json
```

**Every operation involving that user is captured automatically.**

## For Performance Engineers: Profiling & Optimization

### The Problem

Finding performance bottlenecks requires:

- Profiling tools (JProfiler, YourKit)
- Production sampling (risky)
- Manual instrumentation

### The PAL Solution

Every operation is timed and logged:

```bash
# Run with performance logging
pal run --wal perf-log --timing full -cp app.jar

# Later: Analyze performance
pal print -l perf-log --output-format TIMING

# Output shows duration for every method call:
# OrderService.processOrder: 523ms
#   ├─ PaymentService.charge: 450ms  ← bottleneck
#   ├─ InventoryService.reserve: 45ms
#   └─ NotificationService.send: 12ms
```

**Automatic performance profiling with zero instrumentation.**

### Scenario: Slow Request Investigation

**Challenge:** Some requests are slow, but can't reproduce locally.

**Solution:**

```bash
# 1. Identify slow request in production
pal print -l prod-log --filter "duration > 1000ms" --output-format FULL

# 2. Extract that specific execution
pal extract -l prod-log --request-id req-12345 -o /tmp/slow-request.log

# 3. Replay locally to investigate
pal run --source-log /tmp/slow-request.log --timing full -cp app.jar

# 4. See exact timing breakdown of what happened in production
```

**Reproduce production performance issue locally, with exact data and timing.**

## For QA: Regression Testing

### The Problem

Regression testing requires:

- Maintaining large test suites
- Keeping test data up-to-date
- Running tests is slow

### The PAL Solution

Capture production traffic, replay as tests:

```bash
# 1. Capture production traffic for one day
pal run --wal prod-traffic --capture-mode full -cp app.jar

# 2. During QA: Replay production traffic against new version
pal run --source-log prod-traffic -cp app-v2.jar

# 3. Compare outputs
pal diff --log1 prod-traffic --log2 qa-replay --output-format SUMMARY
```

**Test with real production traffic, not manually written test cases.**

### Scenario: Compatibility Testing

**Challenge:** Ensure new version behaves identically to old version.

**Solution:**

```bash
# 1. Capture representative traffic
pal run --wal golden-master -cp app-v1.jar
# Run for 1 hour, capture diverse requests

# 2. Replay against new version
pal run --source-log golden-master -cp app-v2.jar --wal v2-output

# 3. Compare
pal diff --log1 golden-master --log2 v2-output

# Output shows any behavioral differences:
# DIFF in Message 1047:
#   v1 result: 42
#   v2 result: 43  ← regression!
```

**Automated compatibility testing using production traffic as oracle.**

## Combining Use Cases

The power of PAL is that these capabilities work together because they emerge from the same abstraction.

### Example: Full Development Lifecycle

**Development:**
```bash
# Develop locally with Chronicle (fast iteration)
pal run --wal file:/tmp/dev-log --rpc auto -cp target/classes Main
```

**Testing:**
```bash
# Test with interception (no mocks needed)
pal run --interceptable --wal file:/tmp/test-log -cp target/test-classes Tests
```

**Staging:**
```bash
# Deploy to staging with Kafka
pal run -d staging-etcd:2379 -k staging-kafka:9092 \
  --wal staging-log --rpc auto -cp app.jar
```

**Production:**
```bash
# Deploy to production
pal run -d prod-etcd:2379 -k prod-kafka:9092 \
  --wal prod-log --rpc auto --interceptable -cp app.jar
```

**Incident:**
```bash
# Hot-patch production bug via interception
pal intercept -d prod-etcd:2379 -p service-uuid \
  --class BuggyClass --method buggyMethod --type AROUND
```

**Post-Mortem:**
```bash
# Analyze what happened
pal print -l prod-log --from-time "2023-11-09T14:00:00" \
  --to-time "2023-11-09T14:30:00" --output-format FULL

# Replay incident locally
pal extract -l prod-log --time-range "14:00:00-14:30:00" -o /tmp/incident.log
pal run --source-log /tmp/incident.log -cp app.jar
```

**Same tool, same abstraction, different use cases at each stage.**

## Summary

| Role | Primary Use Case | Key Benefit |
|------|------------------|-------------|
| **Developer** | Testing without mocks | Test real code, faster to write |
| **SRE** | Production hot-patching | Fix bugs in 60 seconds, zero downtime |
| **Architect** | Event sourcing & distributed systems | Automatic event capture, transparent RPC |
| **Security** | Audit & compliance | Complete audit trail, zero instrumentation |
| **Performance Engineer** | Profiling & optimization | Automatic timing, production profiling |
| **QA** | Regression testing | Test with real traffic, not manual cases |

All of these emerge from one abstraction: **operations as messages**.

## Next Steps

Choose your path:

- **Developer:** Start with [Local Development Guide](guides/local-development.md)
- **SRE:** See [Testing with Interception](guides/testing-with-interception.md) for hot-patching patterns
- **Architect:** Read [Distributed Application Guide](guides/distributed-application.md)
- **All:** Understand the foundation in [Understanding PAL](understanding-pal.md)
