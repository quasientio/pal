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
- **Execution phase**: BEFORE or AFTER
- **Argument modification**: `ctx.setArg(index, newValue)`
- **Return value access/override**: `ctx.getReturnValue()`, `ctx.setReturnValue(value)`
- **Execution control**: `response.setShouldProceed(false)` (AROUND only)

## Example 1: Argument Modification (BEFORE)

This example converts string arguments to uppercase before method execution:

```java
public class UpperCaseCurrencyCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (ctx.getPhase() == InterceptPhase.BEFORE && ctx.getArgs().length > 0) {
            Object firstArg = ctx.getArgs()[0];
            if (firstArg instanceof String) {
                ctx.setArg(0, ((String) firstArg).toUpperCase());
            }
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
        if (ctx.getPhase() == InterceptPhase.AFTER) {
            CustomerDto dto = (CustomerDto) ctx.getReturnValue();
            if (dto != null) {
                dto.setSsn("***-**-****");
                ctx.setReturnValue(dto);
            }
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

This example implements a caching layer that can skip method execution:

```java
public class CachingCallback implements InterceptCallback {
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        String cacheKey = ctx.getExec().toString();

        if (ctx.getPhase() == InterceptPhase.BEFORE) {
            Object cached = cache.get(cacheKey);
            if (cached != null) {
                // Skip execution and return cached value
                InterceptCallbackResponse response = new InterceptCallbackResponse();
                response.setShouldProceed(false);
                response.setNewReturnValue(cached);
                return response;
            }
        } else if (ctx.getPhase() == InterceptPhase.AFTER) {
            // Cache the result
            cache.put(cacheKey, ctx.getReturnValue());
        }
        return new InterceptCallbackResponse();
    }
}
```

**Use case**: Cache expensive computation results without modifying application code.

**How it works**:
1. **BEFORE phase**: Check cache. If hit, skip method execution and return cached value
2. **AFTER phase**: Store result in cache for next time

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
        if (ctx.getPhase() == InterceptPhase.BEFORE) {
            for (Object arg : ctx.getArgs()) {
                if (arg == null) {
                    throw new IllegalArgumentException("Null argument not allowed");
                }
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

**Important**: Callback handlers must be thread-safe. For `AROUND` intercepts, the same handler instance may be invoked:
- Concurrently for different operations
- Twice sequentially (BEFORE and AFTER phases) for each operation

Use thread-safe data structures (`ConcurrentHashMap`, `CopyOnWriteArrayList`) or synchronization when sharing state.

## Error Handling

Exceptions thrown from callback handlers are propagated to the intercepted peer:

```java
public class AuthorizationCallback implements InterceptCallback {
    @Override
    public InterceptCallbackResponse handle(InterceptContext ctx) {
        if (ctx.getPhase() == InterceptPhase.BEFORE) {
            if (!isAuthorized(ctx)) {
                throw new SecurityException("Unauthorized access");
            }
        }
        return new InterceptCallbackResponse();
    }
}
```

The intercepted method will receive the `SecurityException` as if it were thrown locally.

## Best Practices

1. **Keep callbacks fast**: BEFORE callbacks block method execution
2. **Use AFTER for side effects**: Logging, metrics, auditing don't need to be synchronous
3. **Validate phases**: Always check `ctx.getPhase()` when handling AROUND intercepts
4. **Return empty responses**: If you don't modify execution, return `new InterceptCallbackResponse()`
5. **Test callback isolation**: Ensure handlers don't have unexpected side effects

## See Also

- [Interception Concepts](../concepts/interception.md) - Understanding intercept types
- [Testing with Interception](testing-with-interception.md) - Using intercepts in tests
- [RPC](../concepts/rpc.md) - How callbacks are delivered
