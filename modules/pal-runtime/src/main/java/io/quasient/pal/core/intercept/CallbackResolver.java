/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves intercept callback handlers from either registered callbacks or static method
 * reflection.
 *
 * <p>This class provides a shared callback resolution mechanism used by both:
 *
 * <ul>
 *   <li>{@link IncomingInterceptCallbackDispatcher} - for remote intercept callbacks received via
 *       ZMQ
 *   <li>{@link LocalInterceptCallbackDispatcher} - for local intercept callbacks invoked directly
 * </ul>
 *
 * <p><b>Resolution Strategies:</b>
 *
 * <ol>
 *   <li><b>Registered callbacks:</b> If a {@code registeredCallbackId} is provided, looks up the
 *       callback in the registry
 *   <li><b>Static method reflection:</b> If no registered callback ID is provided, uses reflection
 *       to create a callback wrapper that invokes the static method {@code
 *       callbackClass.callbackMethod}
 * </ol>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The callback registry uses a {@link
 * ConcurrentHashMap} for safe concurrent access.
 */
@Singleton
public class CallbackResolver {

  /** Logger instance for debugging callback resolution operations. */
  private static final Logger logger = LoggerFactory.getLogger(CallbackResolver.class);

  /** Registry of callback handlers by their unique identifiers. */
  private final ConcurrentHashMap<String, InterceptCallback> callbackRegistry =
      new ConcurrentHashMap<>();

  /** Constructs a new CallbackResolver. */
  @Inject
  public CallbackResolver() {
    // No dependencies
  }

  /**
   * Resolves a callback handler from the provided parameters.
   *
   * <p>If {@code registeredCallbackId} is non-null and non-empty, looks it up in the registry.
   * Otherwise, uses reflection to create a callback that invokes the static method {@code
   * callbackClass.callbackMethod}.
   *
   * @param registeredCallbackId the registered callback ID (may be null or empty)
   * @param callbackClass the fully qualified class name for static method resolution
   * @param callbackMethod the method name for static method resolution
   * @return the resolved callback handler
   * @throws IllegalStateException if a registered callback ID is provided but not found
   * @throws IllegalArgumentException if neither registered callback nor valid class/method provided
   * @throws ReflectiveOperationException if reflection fails
   */
  public InterceptCallback resolve(
      String registeredCallbackId, String callbackClass, String callbackMethod)
      throws ReflectiveOperationException {

    if (registeredCallbackId != null && !registeredCallbackId.isEmpty()) {
      // Look up registered callback
      InterceptCallback callback = callbackRegistry.get(registeredCallbackId);
      if (callback == null) {
        throw new IllegalStateException("Registered callback not found: " + registeredCallbackId);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Resolved registered callback: {}", registeredCallbackId);
      }
      return callback;
    } else {
      // Use reflection to invoke static method
      return resolveStaticMethod(callbackClass, callbackMethod);
    }
  }

  /**
   * Resolves a callback handler using static method reflection.
   *
   * <p>The method must be:
   *
   * <ul>
   *   <li>Static
   *   <li>Accept a single {@link InterceptContext} parameter
   *   <li>Return {@link InterceptCallbackResponse}
   * </ul>
   *
   * @param callbackClass the fully qualified class name
   * @param callbackMethod the method name
   * @return a callback wrapper that invokes the static method
   * @throws IllegalArgumentException if the class/method are invalid or don't meet requirements
   * @throws ReflectiveOperationException if reflection fails
   */
  private InterceptCallback resolveStaticMethod(String callbackClass, String callbackMethod)
      throws ReflectiveOperationException {

    if (callbackClass == null || callbackClass.isEmpty()) {
      throw new IllegalArgumentException(
          "Either registeredCallbackId or callbackClass must be provided");
    }

    if (callbackMethod == null || callbackMethod.isEmpty()) {
      throw new IllegalArgumentException("callbackMethod must be provided for static resolution");
    }

    Class<?> clazz =
        Class.forName(callbackClass, true, Thread.currentThread().getContextClassLoader());
    Method method = clazz.getMethod(callbackMethod, InterceptContext.class);

    // Verify method is static
    if (!Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException(
          "Callback method must be static: " + callbackClass + "." + callbackMethod);
    }

    // Verify return type
    if (!InterceptCallbackResponse.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          "Callback method must return InterceptCallbackResponse: "
              + callbackClass
              + "."
              + callbackMethod);
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Resolved static callback method: {}.{}", callbackClass, callbackMethod);
    }

    // Create a wrapper that invokes the static method
    return (ctx) -> (InterceptCallbackResponse) method.invoke(null, ctx);
  }

  /**
   * Registers a callback handler with a unique identifier.
   *
   * <p>Registered callbacks can be resolved via {@code registeredCallbackId} in the {@link
   * #resolve} method.
   *
   * @param callbackId the unique identifier for this callback
   * @param callback the callback handler implementation
   * @throws IllegalArgumentException if callbackId or callback is null/empty
   * @throws IllegalStateException if a callback is already registered with this ID
   */
  public void registerCallback(String callbackId, InterceptCallback callback) {
    if (callbackId == null || callbackId.isEmpty()) {
      throw new IllegalArgumentException("Callback ID must not be null or empty");
    }
    if (callback == null) {
      throw new IllegalArgumentException("Callback must not be null");
    }

    InterceptCallback existing = callbackRegistry.putIfAbsent(callbackId, callback);
    if (existing != null) {
      throw new IllegalStateException("Callback already registered with ID: " + callbackId);
    }

    logger.info("Registered intercept callback: {}", callbackId);
  }

  /**
   * Unregisters a callback handler.
   *
   * @param callbackId the identifier of the callback to unregister
   * @return true if a callback was removed, false if no callback was registered with this ID
   */
  public boolean unregisterCallback(String callbackId) {
    InterceptCallback removed = callbackRegistry.remove(callbackId);
    if (removed != null) {
      logger.info("Unregistered intercept callback: {}", callbackId);
      return true;
    }
    return false;
  }

  /**
   * Checks if a callback is registered with the given ID.
   *
   * @param callbackId the callback ID to check
   * @return true if a callback is registered with this ID
   */
  public boolean isRegistered(String callbackId) {
    return callbackRegistry.containsKey(callbackId);
  }

  /**
   * Returns the number of registered callbacks.
   *
   * @return the count of registered callbacks
   */
  public int getRegisteredCount() {
    return callbackRegistry.size();
  }
}
