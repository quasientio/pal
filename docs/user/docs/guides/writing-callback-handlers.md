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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.PaymentService")
    .methodPattern("processCurrency")
    .interceptType(InterceptType.BEFORE)
    .callbackClass(UpperCaseCurrencyCallback.class.getName())
    .callbackMethod("handle")
    .build();
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.CustomerService")
    .methodPattern("getCustomer")
    .interceptType(InterceptType.AFTER)
    .callbackClass(RedactSsnCallback.class.getName())
    .callbackMethod("handle")
    .build();
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
InterceptRequest intercept = InterceptRequest.builder()
    .classPattern("com.example.ReportService")
    .methodPattern("generateExpensiveReport")
    .interceptType(InterceptType.AROUND)
    .callbackClass(CachingCallback.class.getName())
    .callbackMethod("handle")
    .build();
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

Register with:
```java
.callbackClass("com.example.ValidationHandlers")
.callbackMethod("validateNonNull")
```

## Thread Safety

**Important**: Callback handlers must be thread-safe. The same handler instance may be invoked concurrently for different operations.

Use thread-safe data structures (`ConcurrentHashMap`, `CopyOnWriteArrayList`) or synchronization when sharing state across invocations.

## Error Handling

Exceptions thrown from callback handlers are propagated to the intercepted peer:

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

Register as a BEFORE intercept to reject unauthorized calls before execution. The intercepted method will receive the `SecurityException` as if it were thrown locally.

You can also set exceptions via the context or response:

```java
// Via context
ctx.setExceptionToThrow(new SecurityException("Access denied"));
return new InterceptCallbackResponse();

// Via response
return InterceptCallbackResponse.throwException(new SecurityException("Access denied"));
```

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
