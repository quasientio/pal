# Writing Callback Handlers

Callback handlers are where you implement the logic that runs when an intercept fires. This guide shows practical examples of callback handlers using the `InterceptCallback` interface and `InterceptContext`.

## Callback Handler Basics

All callback handlers implement the `InterceptCallback` functional interface:

```java
public class MyHandler implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) throws Exception {
        // Your logic here
        return new InterceptCallbackResponse();
    }
}
```

The `InterceptContext` provides access to:

- **Operation metadata**: Class name, method name, arguments
- **Argument modification**: `ctx.setArg(index, newValue)` (BEFORE, AROUND before proceed)
- **Return value access/override**: `ctx.getReturnValue()`, `ctx.setReturnValue(value)` (AFTER, AROUND after proceed)
- **Execution control**: `ctx.proceed()` and `InterceptCallbackResponse.skipProceed()` (AROUND only)

## Example 1: Argument Modification (BEFORE)

This example converts string arguments to uppercase before method execution:

```java
public class UpperCaseCurrencyCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        Object[] args = ctx.getArgs();
        if (args.length > 0 && args[0] instanceof String) {
            ctx.setArg(0, ((String) args[0]).toUpperCase());
        }
        return new InterceptCallbackResponse();
    }
}
```

**Use case**: Normalize currency codes (e.g., "usd" → "USD") before they reach business logic.

**How to use**:
```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.PaymentService",
    UpperCaseCurrencyCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("processCurrency", Collections.emptyList()));
```

## Example 2: Return Value Override (AFTER)

This example redacts sensitive data in return values:

```java
public class RedactSsnCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        CustomerDto dto = (CustomerDto) ctx.getReturnValue();
        if (dto != null) {
            dto.setSsn("***-**-****");
            ctx.setReturnValue(dto);
        }
        return new InterceptCallbackResponse();
    }
}
```

**Use case**: Automatically redact sensitive fields in DTOs before they're logged or sent to external systems.

**How to use**:
```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.AFTER,
    "com.example.CustomerService",
    RedactSsnCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("getCustomer", Collections.emptyList()));
```

## Example 3: Caching with Execution Control (AROUND)

This example implements a caching layer that can skip method execution. AROUND intercepts use `ctx.proceed()` to call the original method, giving you full control over execution flow:

```java
public class CachingCallback implements InterceptCallback {
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        Object[] args = ctx.getArgs();
        String cacheKey = args.length > 0 ? String.valueOf(args[0]) : "";

        // Check cache first
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            // Cache hit: skip method execution and return cached value
            ctx.setReturnValue(cached);
            return InterceptCallbackResponse.skipProceed();
        }

        // Cache miss: proceed with method execution
        ProceedResult result = ctx.proceed();

        // Cache the result if no exception was thrown
        if (!result.hasException()) {
            cache.put(cacheKey, result.getReturnValue());
        }

        return new InterceptCallbackResponse();
    }
}
```

**Use case**: Cache expensive computation results without modifying application code.

**How it works**:

1. Check cache for existing result
2. On cache hit: set return value via `ctx.setReturnValue()` and skip execution with `skipProceed()`
3. On cache miss: call `ctx.proceed()` to execute the method, then cache the result

**Key AROUND patterns**:

- `ctx.proceed()` - Execute the original method, returns `ProceedResult`
- `ctx.setReturnValue(value)` + `skipProceed()` - Skip execution and return custom value
- `result.hasException()` - Check if method threw an exception
- `result.getReturnValue()` - Get the method's return value after proceed

**How to use**:
```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.AROUND,
    "com.example.ReportService",
    CachingCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("generateExpensiveReport", Collections.emptyList()));
```

## Static Callback Methods

If you don't need instance state, use static methods:

```java
public class ValidationHandlers {
    public static InterceptCallbackResponse validateNonNull(InterceptContext ctx) {
        for (Object arg : ctx.getArgs()) {
            if (arg == null) {
                throw new IllegalArgumentException("Null argument not allowed");
            }
        }
        return new InterceptCallbackResponse();
    }
}
```

Register with callbackClass and callbackMethod in the InterceptRequest constructor:
```java
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    "com.example.ValidationHandlers",  // callbackClass
    "validateNonNull",                 // callbackMethod
    new InterceptableMethodCall("*", Collections.emptyList()));
```

## Thread Safety

**Important**: Callback handlers must be thread-safe. The same handler instance may be invoked concurrently for different operations.

Use thread-safe data structures (`ConcurrentHashMap`, `CopyOnWriteArrayList`) or synchronization when sharing state across invocations.

## Exception Handling

PAL provides fine-grained control over how exceptions from callback handlers are propagated to the intercepted code. Understanding exception handling is crucial for writing robust callback handlers.

### Exception Types

PAL distinguishes between two categories of exceptions:

#### 1. API Misuse Exceptions

These are programming errors that indicate incorrect usage of the intercept API. They always propagate to help you catch bugs during development:

**InterceptTypeNotSupportedException** - Thrown when you call an operation not supported for the current intercept type:

```java
public class BadCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        // ERROR: BEFORE intercepts can't access return values
        Object value = ctx.getReturnValue();  // throws InterceptTypeNotSupportedException
        return new InterceptCallbackResponse();
    }
}
```

**Common violations**:

- `getReturnValue()` in BEFORE intercepts (no return value yet)
- `proceed()` in BEFORE/AFTER intercepts (only AROUND can proceed)
- `setArg()` in AFTER intercepts (arguments already consumed)

**InterceptPhaseViolationException** - Thrown when you call an operation during the wrong phase of an AROUND intercept:

```java
public class AroundCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        ctx.setArg(0, "modified");  // OK: before proceed

        ProceedResult result = ctx.proceed();

        ctx.setArg(0, "too late");  // throws InterceptPhaseViolationException
        return new InterceptCallbackResponse();
    }
}
```

**Phase rules for AROUND intercepts**:

- **Before proceed**: Can call `setArg()`, `getArgs()`, `proceed()`
- **After proceed**: Can call `getReturnValue()`, `setReturnValue()`, `setExceptionToThrow()`
- Cannot call `proceed()` twice

**How to fix**: These exceptions point to programming errors. Read the exception message to understand what operation is not allowed and why. Update your callback logic to follow the API contract.

#### 2. Business Logic Exceptions

These are exceptions you intentionally throw to signal errors or control execution flow (e.g., `SecurityException`, `IllegalArgumentException`, custom exceptions). How they propagate depends on the configured exception policies.

### Exception Propagation Policies

Exception propagation policies control whether exceptions from callback handlers reach the intercepted code. There are four policies:

**PROPAGATE_CONTROLLED_ONLY** (default) - Only propagate exceptions that are both successfully executed AND explicitly set:

```java
public class ValidatedCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (!isValid(ctx.getArgs()[0])) {
            // This will propagate (explicitly set via setExceptionToThrow)
            ctx.setExceptionToThrow(new IllegalArgumentException("Invalid input"));
        }
        return new InterceptCallbackResponse();
    }
}
```

If your callback throws an exception (crashes), it's logged but swallowed - the intercepted code continues normally. This prevents buggy callbacks from breaking production code.

**PROPAGATE_EXPLICIT_ONLY** - Only propagate exceptions set via `setExceptionToThrow()`:

```java
public class ExplicitOnlyCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        // This will NOT propagate (callback crashed)
        throw new RuntimeException("Callback bug");
    }
}
```

Use this when you want fine-grained control - only exceptions you explicitly set will propagate.

**PROPAGATE_ALL** - Propagate all exceptions, including callback crashes:

```java
public class StrictCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        // This WILL propagate (callback crashed)
        throw new RuntimeException("Callback crashed");
    }
}
```

Use this in testing or development to catch callback bugs early. **Warning**: In production, callback bugs will break intercepted code.

**SWALLOW_ALL** - Never propagate exceptions:

```java
public class MonitoringCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        // Exceptions are logged but never propagate
        metrics.record(ctx.getMethod(), ctx.getArgs());
        throw new RuntimeException("Metrics system down");  // swallowed
        return new InterceptCallbackResponse();
    }
}
```

Use this for non-critical monitoring or logging callbacks where failures shouldn't affect application behavior.

### Checked Exception Validation

Java's checked exception mechanism requires methods to declare all checked exceptions they might throw. PAL validates callback exceptions against the intercepted method's signature:

```java
// Intercepted method declares IOException
public String readFile(String path) throws IOException {
    // ...
}

// Callback tries to throw SQLException (not declared)
public class BadCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) throws Exception {
        ctx.setExceptionToThrow(new SQLException("Database error"));
        return new InterceptCallbackResponse();
    }
}
```

PAL provides three policies to handle this mismatch:

**WRAP** (default) - Wrap undeclared checked exceptions in RuntimeException:

```java
// The SQLException is wrapped:
// RuntimeException("Exception from intercept callback", cause: SQLException)
```

This preserves Java's type safety while allowing callbacks to throw any exception.

**REJECT** - Throw `InvalidCallbackExceptionException` to signal the violation:

```java
// Throws InvalidCallbackExceptionException:
// "Callback threw checked exception java.sql.SQLException which is not
//  compatible with declared exceptions: [java.io.IOException]"
```

Use this in testing to catch incorrect exception types early.

**ALLOW_ALL** - Bypass validation and allow any exception:

```java
// The SQLException propagates directly (violates Java type safety)
```

**Warning**: This can cause `UndeclaredThrowableException` or break exception handling code that assumes methods only throw declared exceptions. Only use this if you understand the risks.

**Note**: Unchecked exceptions (RuntimeException, Error) always propagate without validation regardless of policy.

### Setting Exceptions

There are three ways to set exceptions in callbacks:

**1. Throw directly** (simple but limited control):

```java
public class AuthorizationCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (!isAuthorized(ctx)) {
            throw new SecurityException("Unauthorized access");
        }
        return new InterceptCallbackResponse();
    }
}
```

**Propagation**: Depends on policy (PROPAGATE_ALL or PROPAGATE_CONTROLLED_ONLY)

**2. Set via context** (recommended):

```java
public class ValidationCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (!isValid(ctx.getArgs()[0])) {
            ctx.setExceptionToThrow(new IllegalArgumentException("Invalid input"));
        }
        return new InterceptCallbackResponse();
    }
}
```

**Propagation**: Always propagates with PROPAGATE_EXPLICIT_ONLY or PROPAGATE_CONTROLLED_ONLY

**3. Set via response** (alternative syntax):

```java
public class ValidationCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (!isValid(ctx.getArgs()[0])) {
            return InterceptCallbackResponse.throwException(
                new IllegalArgumentException("Invalid input")
            );
        }
        return new InterceptCallbackResponse();
    }
}
```

**Recommendation**: Use `ctx.setExceptionToThrow()` or response-based `throwException()` for predictable, explicit exception propagation.

### Configuring Exception Policies

Exception policies can be configured at three levels:

#### 1. Global Default (applies to all intercepts)

Via CLI flags:
```bash
pal run --exception-policy PROPAGATE_ALL \
        --checked-exception-policy REJECT \
        -cp app.jar com.example.App
```

Via environment variables:
```bash
export EXCEPTION_POLICY=PROPAGATE_ALL
export CHECKED_EXCEPTION_POLICY=REJECT
pal run -cp app.jar com.example.App
```

Via system properties:
```bash
java -Dpal.intercept.exception-policy.default=PROPAGATE_ALL \
     -Dpal.intercept.checked-exception-policy.default=REJECT \
     -jar app.jar
```

#### 2. Per-Type Default (specific to BEFORE, AFTER, or AROUND intercepts)

Via system properties:
```bash
# Strict policies for BEFORE intercepts (validation)
java -Dpal.intercept.exception-policy.before=PROPAGATE_ALL \
     -Dpal.intercept.checked-exception-policy.before=REJECT \
     # Lenient policies for AFTER intercepts (monitoring)
     -Dpal.intercept.exception-policy.after=SWALLOW_ALL \
     -jar app.jar
```

#### 3. Per-Intercept Override (most specific)

Via InterceptRequest:
```java
InterceptRequest<InterceptableMethodCall> intercept = new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.PaymentService",
    AuthCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("processPayment", Collections.emptyList()),
    false,
    ExceptionPropagationPolicy.PROPAGATE_ALL,
    CheckedExceptionPolicy.REJECT);
```

**Resolution order**: Per-intercept override → Per-type default → Global default

### Common Patterns

#### Pattern 1: Strict Validation (BEFORE intercept)

```java
// Use PROPAGATE_ALL to catch bugs early in testing
new InterceptRequest<>(
    UUID.randomUUID(),
    callbackPeerUuid,
    InterceptType.BEFORE,
    "com.example.Service",
    ValidationCallback.class.getName(),
    "handle",
    new InterceptableMethodCall("*", Collections.emptyList()),
    false,
    ExceptionPropagationPolicy.PROPAGATE_ALL,
    CheckedExceptionPolicy.REJECT);
```

#### Pattern 2: Resilient Monitoring (AFTER intercept)

```java
// Use SWALLOW_ALL so monitoring failures don't break application
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

#### Pattern 3: Production Authorization (BEFORE intercept)

```java
// Use PROPAGATE_CONTROLLED_ONLY - explicit denials propagate, bugs don't
public class AuthCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (!isAuthorized(ctx)) {
            // This will propagate
            ctx.setExceptionToThrow(new SecurityException("Access denied"));
        }
        // If callback crashes here, exception is swallowed
        return new InterceptCallbackResponse();
    }
}
```

### Exception Handling in AROUND Intercepts

AROUND intercepts can handle exceptions from the original method:

```java
public class ExceptionHandlingCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        ProceedResult result = ctx.proceed();

        if (result.hasException()) {
            Throwable exception = result.getException();

            // Option 1: Suppress the exception
            ctx.setReturnValue(getDefaultValue());
            ctx.setExceptionToThrow(null);

            // Option 2: Replace the exception
            ctx.setExceptionToThrow(new CustomException("Wrapped", exception));

            // Option 3: Let it propagate (do nothing)
        }

        return new InterceptCallbackResponse();
    }
}
```

### Async Intercepts and Exceptions

**BEFORE_ASYNC** and **AFTER_ASYNC** intercepts always use `SWALLOW_ALL` policy regardless of configuration:

```java
// This exception will be logged but never propagate
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

**Reason**: Async intercepts don't block the caller, so there's no synchronous path to propagate exceptions.

## Best Practices

1. **Keep callbacks fast**: BEFORE and AROUND callbacks block method execution
2. **Use AFTER for side effects**: Logging, metrics, auditing don't need to be synchronous
3. **Use `proceed()` in AROUND**: Call `ctx.proceed()` to execute the method, or `skipProceed()` to skip it
4. **Always set return value before skip**: When using `skipProceed()`, first call `ctx.setReturnValue(value)`
5. **Return empty responses**: If you don't modify execution, return `new InterceptCallbackResponse()`
6. **Test callback isolation**: Ensure handlers don't have unexpected side effects

## See Also

- [Interception Concepts](../concepts/interception.md) - Understanding intercept types
- [Testing with Interception](testing-with-interception.md) - Using intercepts in tests
- [RPC](../concepts/rpc.md) - How callbacks are delivered
