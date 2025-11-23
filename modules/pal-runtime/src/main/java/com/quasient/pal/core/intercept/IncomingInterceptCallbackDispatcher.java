/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import com.quasient.pal.common.lang.intercept.InterceptCallback;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.messages.colfer.InterceptCallbackRequest;
import com.quasient.pal.messages.colfer.InterceptCallbackResponse;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.colfer.ExceptionSerdes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming intercept callback requests by invoking registered or static callback handlers.
 *
 * <p>This dispatcher receives {@link InterceptCallbackRequest} messages from intercepted peers,
 * routes them to the appropriate callback handler, constructs an {@link InterceptContext}, invokes
 * the callback, and returns an {@link InterceptCallbackResponse}.
 *
 * <p><b>Callback Resolution:</b>
 *
 * <ul>
 *   <li>If {@code registeredCallbackId} is present, looks up a registered {@link InterceptCallback}
 *       instance
 *   <li>Otherwise, uses reflection to invoke a static method: {@code callbackClass.callbackMethod}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Callback handlers must also be thread-safe,
 * as they may be invoked concurrently for different intercept requests.
 */
@Singleton
public class IncomingInterceptCallbackDispatcher {

  /** Logger instance for debugging callback dispatch operations. */
  private static final Logger logger =
      LoggerFactory.getLogger(IncomingInterceptCallbackDispatcher.class);

  /** Registry of callback handlers by their unique identifiers. */
  private final ConcurrentHashMap<String, InterceptCallback> callbackRegistry =
      new ConcurrentHashMap<>();

  /** Constructs a new IncomingInterceptCallbackDispatcher. */
  @Inject
  public IncomingInterceptCallbackDispatcher() {
    // No dependencies yet
  }

  /**
   * Handles an incoming intercept callback request.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Resolves the callback handler (registered or static method)
   *   <li>Builds an {@link InterceptContext} from the request
   *   <li>Invokes {@link InterceptCallback#handle(InterceptContext)}
   *   <li>Constructs an {@link InterceptCallbackResponse} from the result
   * </ol>
   *
   * @param request the intercept callback request from the intercepted peer
   * @return the callback response to send back to the intercepted peer
   */
  public InterceptCallbackResponse handleCallback(InterceptCallbackRequest request) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Handling intercept callback: callbackId={}, phase={}, interceptType={}",
          request.getCallbackId(),
          request.getPhase(),
          request.getInterceptType());
    }

    try {
      // Resolve the callback handler
      InterceptCallback callback = resolveCallback(request);

      // Build the context
      InterceptContext context = buildContext(request);

      // Invoke the callback
      com.quasient.pal.common.lang.intercept.InterceptCallbackResponse userResponse =
          callback.handle(context);

      // Build the wire response
      InterceptCallbackResponse wireResponse = buildWireResponse(request, context, userResponse);

      if (logger.isDebugEnabled()) {
        logger.debug("Callback completed successfully: callbackId={}", request.getCallbackId());
      }

      return wireResponse;

    } catch (Exception ex) {
      logger.error("Error handling intercept callback: callbackId={}", request.getCallbackId(), ex);

      // Return error response
      return buildErrorResponse(request, ex);
    }
  }

  /**
   * Resolves the callback handler from the request.
   *
   * <p>If {@code registeredCallbackId} is present, looks it up in the registry. Otherwise, uses
   * reflection to create a callback that invokes the static method {@code callbackClass}.{@code
   * callbackMethod}.
   *
   * @param request the callback request
   * @return the resolved callback handler
   * @throws Exception if the callback cannot be resolved
   */
  private InterceptCallback resolveCallback(InterceptCallbackRequest request) throws Exception {
    String registeredCallbackId = request.getRegisteredCallbackId();

    if (registeredCallbackId != null && !registeredCallbackId.isEmpty()) {
      // Look up registered callback
      InterceptCallback callback = callbackRegistry.get(registeredCallbackId);
      if (callback == null) {
        throw new IllegalStateException("Registered callback not found: " + registeredCallbackId);
      }
      return callback;
    } else {
      // Use reflection to invoke static method
      String className = request.getCallbackClass();
      String methodName = request.getCallbackMethod();

      if (className == null || className.isEmpty()) {
        throw new IllegalArgumentException(
            "Either registeredCallbackId or callbackClass must be provided");
      }

      Class<?> callbackClass =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Method callbackMethod = callbackClass.getMethod(methodName, InterceptContext.class);

      // Verify method is static and returns InterceptCallbackResponse
      if (!java.lang.reflect.Modifier.isStatic(callbackMethod.getModifiers())) {
        throw new IllegalArgumentException(
            "Callback method must be static: " + className + "." + methodName);
      }

      if (!com.quasient.pal.common.lang.intercept.InterceptCallbackResponse.class.isAssignableFrom(
          callbackMethod.getReturnType())) {
        throw new IllegalArgumentException(
            "Callback method must return InterceptCallbackResponse: "
                + className
                + "."
                + methodName);
      }

      // Create a wrapper that invokes the static method
      return (ctx) ->
          (com.quasient.pal.common.lang.intercept.InterceptCallbackResponse)
              callbackMethod.invoke(null, ctx);
    }
  }

  /**
   * Builds an {@link InterceptContext} from the callback request.
   *
   * @param request the callback request
   * @return the context for the callback
   * @throws Exception if context building fails
   */
  private InterceptContext buildContext(InterceptCallbackRequest request) throws Exception {
    InterceptPhase phase = InterceptPhase.fromByte(request.getPhase());
    InterceptType interceptType = InterceptType.fromByte(request.getInterceptType());

    // Deserialize arguments from ExecMessage
    Object[] args = extractArguments(request);

    if (phase == InterceptPhase.BEFORE) {
      return InterceptContext.forBeforePhase(
          request.getExec(), interceptType, request.getInterceptedPeer(), args);
    } else {
      // AFTER phase
      Object returnValue = extractReturnValue(request);
      Throwable thrownException = extractThrownException(request);

      return InterceptContext.forAfterPhase(
          request.getExec(),
          interceptType,
          request.getInterceptedPeer(),
          args,
          returnValue,
          request.getIsVoid(),
          thrownException);
    }
  }

  /**
   * Extracts arguments from the ExecMessage.
   *
   * @param request the callback request
   * @return the deserialized arguments array
   */
  private Object[] extractArguments(InterceptCallbackRequest request) {
    com.quasient.pal.messages.colfer.ExecMessage exec = request.getExec();
    if (exec == null) {
      return new Object[0];
    }

    // Extract parameters based on message type
    com.quasient.pal.messages.colfer.Parameter[] parameters = null;

    if (exec.getConstructorCall() != null) {
      parameters = exec.getConstructorCall().getParameters();
    } else if (exec.getInstanceMethodCall() != null) {
      parameters = exec.getInstanceMethodCall().getParameters();
    } else if (exec.getClassMethodCall() != null) {
      parameters = exec.getClassMethodCall().getParameters();
    }

    if (parameters == null || parameters.length == 0) {
      return new Object[0];
    }

    // Deserialize each parameter
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      try {
        Obj paramValue = parameters[i].getValue();
        if (paramValue.getIsNull()) {
          args[i] = null;
        } else {
          args[i] = Unwrapper.unwrapObject(paramValue);
        }
      } catch (Exception e) {
        logger.warn("Failed to deserialize argument {}: {}", i, e.getMessage());
        args[i] = null;
      }
    }

    return args;
  }

  /**
   * Extracts the return value from the request (AFTER phase only).
   *
   * @param request the callback request
   * @return the deserialized return value, or null
   * @throws Exception if deserialization fails
   */
  private Object extractReturnValue(InterceptCallbackRequest request) throws Exception {
    if (request.getIsVoid()) {
      return null;
    }

    Obj returnValueObj = request.getReturnValue();
    if (returnValueObj == null) {
      return null;
    }

    return Unwrapper.unwrapObject(returnValueObj);
  }

  /**
   * Extracts the thrown exception from the request (AFTER phase only).
   *
   * @param request the callback request
   * @return the deserialized exception, or null
   */
  private Throwable extractThrownException(InterceptCallbackRequest request) {
    com.quasient.pal.messages.colfer.RaisedThrowable raised = request.getThrownException();
    if (raised == null) {
      return null;
    }

    return ExceptionSerdes.deserializeException(raised);
  }

  /**
   * Builds a wire response from the callback result.
   *
   * @param request the original request
   * @param context the context used for the callback
   * @param userResponse the response from the callback handler
   * @return the wire-format response
   */
  private InterceptCallbackResponse buildWireResponse(
      InterceptCallbackRequest request,
      InterceptContext context,
      com.quasient.pal.common.lang.intercept.InterceptCallbackResponse userResponse) {

    InterceptCallbackResponse wireResponse = new InterceptCallbackResponse();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(request.getPhase());

    InterceptPhase phase = InterceptPhase.fromByte(request.getPhase());
    InterceptType interceptType = InterceptType.fromByte(request.getInterceptType());

    if (phase == InterceptPhase.BEFORE) {
      // Handle argument mutation
      if (context.isArgsModified()) {
        wireResponse.setMutatedArgs(serializeArgs(context.getArgsInternal()));
      }

      // Handle proceed control (AROUND only)
      // BEFORE and AFTER intercepts cannot control execution flow
      if (interceptType == InterceptType.AROUND) {
        wireResponse.setShouldProceed(userResponse.isShouldProceed());
      } else {
        wireResponse.setShouldProceed(true);
      }

    } else {
      // AFTER phase - handle return value override
      if (context.isReturnValueModified()) {
        wireResponse.setOverrideReturn(true);
        wireResponse.setNewReturnValue(serializeObject(context.getReturnValue()));
      }
    }

    // Handle exceptions (both phases)
    if (userResponse.getExceptionToThrow() != null) {
      wireResponse.setThrowException(true);
      wireResponse.setException(serializeException(userResponse.getExceptionToThrow()));
    }

    return wireResponse;
  }

  /**
   * Builds an error response when callback invocation fails.
   *
   * @param request the original request
   * @param error the exception that occurred
   * @return the error response
   */
  private InterceptCallbackResponse buildErrorResponse(
      InterceptCallbackRequest request, Exception error) {
    InterceptCallbackResponse wireResponse = new InterceptCallbackResponse();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(request.getPhase());
    wireResponse.setThrowException(true);
    wireResponse.setException(serializeException(error));
    return wireResponse;
  }

  /**
   * Serializes an array of arguments to Colfer Obj format.
   *
   * @param args the arguments to serialize
   * @return the serialized arguments array
   */
  private Obj[] serializeArgs(Object[] args) {
    if (args == null || args.length == 0) {
      return new Obj[0];
    }

    Obj[] serialized = new Obj[args.length];
    for (int i = 0; i < args.length; i++) {
      serialized[i] = serializeObject(args[i]);
    }
    return serialized;
  }

  /**
   * Serializes a single object to Colfer Obj format.
   *
   * @param value the object to serialize
   * @return the serialized object
   */
  private Obj serializeObject(Object value) {
    Obj obj = new Obj();

    if (value == null) {
      obj.setIsNull(true);
      return obj;
    }

    String className = value.getClass().getName();

    // Use FORCE_BY_VALUE to serialize the actual value, not just a reference
    return com.quasient.pal.serdes.colfer.Wrapper.wrapInto(
        obj, value, className, null, com.quasient.pal.serdes.colfer.WrapPolicy.FORCE_BY_VALUE);
  }

  /**
   * Serializes an exception to Colfer RaisedThrowable format.
   *
   * @param throwable the exception to serialize
   * @return the serialized exception
   */
  private com.quasient.pal.messages.colfer.RaisedThrowable serializeException(Throwable throwable) {
    return ExceptionSerdes.serializeException(throwable);
  }

  /**
   * Registers a callback handler with a unique identifier.
   *
   * <p>Registered callbacks can be invoked via {@code registeredCallbackId} in the callback
   * request.
   *
   * @param callbackId the unique identifier for this callback
   * @param callback the callback handler implementation
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
}
