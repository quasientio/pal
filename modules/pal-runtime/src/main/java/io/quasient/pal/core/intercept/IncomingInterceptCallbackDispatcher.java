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

import io.quasient.pal.common.lang.intercept.AroundSocketAccessor;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.ExceptionSerdes;
import io.quasient.pal.serdes.colfer.Wrapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming intercept callback requests by invoking registered or static callback handlers.
 *
 * <p>This dispatcher receives {@link InterceptCallbackRequestMessage} messages from intercepted
 * peers, routes them to the appropriate callback handler, constructs an {@link InterceptContext},
 * invokes the callback, and returns an {@link InterceptCallbackResponseMessage}.
 *
 * <p><b>Callback Resolution:</b>
 *
 * <ul>
 *   <li>If {@code registeredCallbackId} is present, looks up a registered {@link InterceptCallback}
 *       instance via {@link CallbackResolver}
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

  /** Shared callback resolver for looking up registered callbacks and resolving static methods. */
  private final CallbackResolver callbackResolver;

  /**
   * Constructs a new IncomingInterceptCallbackDispatcher.
   *
   * @param callbackResolver the shared callback resolver
   */
  @Inject
  public IncomingInterceptCallbackDispatcher(CallbackResolver callbackResolver) {
    this.callbackResolver = callbackResolver;
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
   *   <li>Constructs an {@link InterceptCallbackResponseMessage} from the result
   * </ol>
   *
   * @param request the intercept callback request from the intercepted peer
   * @return the callback response to send back to the intercepted peer
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public InterceptCallbackResponseMessage handleCallback(InterceptCallbackRequestMessage request) {
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
      InterceptCallbackResponse userResponse = callback.handle(context);

      // Build the wire response
      InterceptCallbackResponseMessage wireResponse =
          buildWireResponse(request, context, userResponse);

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
   * <p>Delegates to {@link CallbackResolver} for callback resolution. If {@code
   * registeredCallbackId} is present, looks it up in the registry. Otherwise, uses reflection to
   * create a callback that invokes the static method {@code callbackClass.callbackMethod}.
   *
   * @param request the callback request
   * @return the resolved callback handler
   * @throws Exception if the callback cannot be resolved
   */
  private InterceptCallback resolveCallback(InterceptCallbackRequestMessage request)
      throws Exception {
    return callbackResolver.resolve(
        request.getRegisteredCallbackId(), request.getCallbackClass(), request.getCallbackMethod());
  }

  /**
   * Builds an {@link InterceptContext} from the callback request.
   *
   * @param request the callback request
   * @return the context for the callback
   * @throws Exception if context building fails
   */
  private InterceptContext buildContext(InterceptCallbackRequestMessage request) throws Exception {
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
   * <p>For method and constructor calls, arguments are extracted from the parameters array. For
   * field PUT operations, the value being set is treated as a single argument at index 0.
   *
   * @param request the callback request
   * @return the deserialized arguments array
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private Object[] extractArguments(InterceptCallbackRequestMessage request) {
    ExecMessage exec = request.getExec();
    if (exec == null) {
      return new Object[0];
    }

    // Handle field PUT operations - value being set is the single argument
    if (exec.getInstanceFieldPut() != null) {
      Obj valueObj = exec.getInstanceFieldPut().getValueObject();
      return extractFieldPutArgument(valueObj);
    }
    if (exec.getStaticFieldPut() != null) {
      Obj valueObj = exec.getStaticFieldPut().getValueObject();
      return extractFieldPutArgument(valueObj);
    }

    // Handle field GET operations - no arguments
    if (exec.getInstanceFieldGet() != null || exec.getStaticFieldGet() != null) {
      return new Object[0];
    }

    // Extract parameters for method/constructor calls
    Parameter[] parameters = null;

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
    // Throws on deserialization failure rather than silently returning null,
    // because null is a valid argument value.
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      Obj paramValue = parameters[i].getValue();
      if (paramValue.getIsNull()) {
        args[i] = null;
      } else {
        try {
          args[i] = Unwrapper.unwrapObject(paramValue);
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Failed to deserialize argument " + i + ": " + e.getMessage(), e);
        }
      }
    }

    return args;
  }

  /**
   * Extracts the field PUT value as a single-element argument array.
   *
   * <p>This method throws an exception on deserialization failure rather than silently returning
   * null, because null is a valid PUT value and the callback handler must be able to distinguish
   * between "value is null" and "deserialization failed".
   *
   * @param valueObj the serialized value object (may be null)
   * @return a single-element array containing the deserialized value
   * @throws IllegalArgumentException if deserialization fails
   */
  private Object[] extractFieldPutArgument(Obj valueObj) {
    if (valueObj == null) {
      return new Object[] {null};
    }
    if (valueObj.getIsNull()) {
      return new Object[] {null};
    }
    try {
      return new Object[] {Unwrapper.unwrapObject(valueObj)};
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to deserialize field PUT value: " + e.getMessage(), e);
    }
  }

  /**
   * Extracts the return value from the request (AFTER phase only).
   *
   * @param request the callback request
   * @return the deserialized return value, or null
   * @throws Exception if deserialization fails
   */
  private Object extractReturnValue(InterceptCallbackRequestMessage request) throws Exception {
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
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private Throwable extractThrownException(InterceptCallbackRequestMessage request) {
    RaisedThrowable raised = request.getThrownException();
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
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private InterceptCallbackResponseMessage buildWireResponse(
      InterceptCallbackRequestMessage request,
      InterceptContext context,
      InterceptCallbackResponse userResponse) {

    InterceptCallbackResponseMessage wireResponse = new InterceptCallbackResponseMessage();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(request.getPhase());

    InterceptPhase phase = InterceptPhase.fromByte(request.getPhase());
    InterceptType interceptType = InterceptType.fromByte(request.getInterceptType());

    if (phase == InterceptPhase.BEFORE) {
      // Handle argument mutation
      if (context.isArgsModified()) {
        wireResponse.setMutatedArgs(Wrapper.wrapArgsForceByValue(context.getArgsInternal()));
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
        wireResponse.setNewReturnValue(Wrapper.wrapForceByValue(context.getReturnValue()));
      }
    }

    // Handle exceptions (both phases) - check both context and response
    Throwable exceptionToThrow = context.getExceptionToThrow();
    if (exceptionToThrow == null) {
      exceptionToThrow = userResponse.getExceptionToThrow();
    }
    if (exceptionToThrow != null) {
      wireResponse.setThrowException(true);
      wireResponse.setException(serializeException(exceptionToThrow));
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
  private InterceptCallbackResponseMessage buildErrorResponse(
      InterceptCallbackRequestMessage request, Exception error) {
    InterceptCallbackResponseMessage wireResponse = new InterceptCallbackResponseMessage();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(request.getPhase());
    wireResponse.setThrowException(true);

    // Unwrap InvocationTargetException to get the real exception
    Throwable exceptionToSerialize = error;
    if (error instanceof java.lang.reflect.InvocationTargetException) {
      Throwable cause = error.getCause();
      if (cause != null) {
        exceptionToSerialize = cause;
      }
    }

    wireResponse.setException(serializeException(exceptionToSerialize));
    return wireResponse;
  }

  /**
   * Serializes an exception to Colfer RaisedThrowable format.
   *
   * @param throwable the exception to serialize
   * @return the serialized exception
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private RaisedThrowable serializeException(Throwable throwable) {
    return ExceptionSerdes.serializeException(throwable);
  }

  /**
   * Registers a callback handler with a unique identifier.
   *
   * <p>Registered callbacks can be invoked via {@code registeredCallbackId} in the callback
   * request. Delegates to {@link CallbackResolver}.
   *
   * @param callbackId the unique identifier for this callback
   * @param callback the callback handler implementation
   */
  public void registerCallback(String callbackId, InterceptCallback callback) {
    callbackResolver.registerCallback(callbackId, callback);
  }

  /**
   * Unregisters a callback handler. Delegates to {@link CallbackResolver}.
   *
   * @param callbackId the identifier of the callback to unregister
   * @return true if a callback was removed, false if no callback was registered with this ID
   */
  public boolean unregisterCallback(String callbackId) {
    return callbackResolver.unregisterCallback(callbackId);
  }

  // ---- AROUND intercept support with ctx.proceed() API ----

  /**
   * Handles an AROUND intercept callback with socket accessor for proceed() support.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Resolves the callback handler
   *   <li>Builds an {@link InterceptContext} with the socket accessor injected
   *   <li>Invokes the callback once (the callback can call proceed())
   *   <li>Returns the appropriate response based on whether proceed() was called
   * </ol>
   *
   * @param request the AROUND intercept BEFORE phase request
   * @param socketAccessor the accessor for socket operations during proceed()
   * @return the final response (AFTER if proceed() was called, BEFORE skip if not)
   */
  public InterceptCallbackResponseMessage handleAroundCallback(
      InterceptCallbackRequestMessage request, AroundSocketAccessor socketAccessor) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Handling AROUND callback: callbackId={}, interceptType={}",
          request.getCallbackId(),
          request.getInterceptType());
    }

    try {
      // Resolve the callback handler
      InterceptCallback callback = resolveCallback(request);

      // Build context for AROUND with accessor injected
      InterceptContext context = buildAroundContext(request, socketAccessor);

      // Invoke the callback (may call proceed() internally)
      InterceptCallbackResponse userResponse = callback.handle(context);

      // Build response based on whether proceed() was called
      InterceptCallbackResponseMessage wireResponse;
      if (context.isProceedCalled()) {
        // proceed() was called - build AFTER phase response
        wireResponse = buildAfterResponse(request, context, userResponse);
      } else {
        // proceed() was NOT called - build BEFORE skip response
        wireResponse = buildSkipResponse(request, context, userResponse);
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "AROUND callback completed: callbackId={}, proceedCalled={}",
            request.getCallbackId(),
            context.isProceedCalled());
      }

      return wireResponse;

    } catch (Exception ex) {
      logger.error(
          "Error handling AROUND intercept callback: callbackId={}", request.getCallbackId(), ex);
      return buildErrorResponse(request, ex);
    }
  }

  /**
   * Builds an {@link InterceptContext} for AROUND intercept with socket accessor.
   *
   * @param request the callback request
   * @param socketAccessor the accessor for socket operations
   * @return the context for the callback
   */
  private InterceptContext buildAroundContext(
      InterceptCallbackRequestMessage request, AroundSocketAccessor socketAccessor) {
    // Extract arguments
    Object[] args = extractArguments(request);

    // Create context in BEFORE phase with AROUND type
    InterceptContext context =
        InterceptContext.forBeforePhase(
            request.getExec(), InterceptType.AROUND, request.getInterceptedPeer(), args);

    // Inject the socket accessor for proceed()
    int timeoutMs = request.getTimeoutMs();
    context.setAroundAccessor(socketAccessor, request.getCallbackId(), timeoutMs);

    return context;
  }

  /**
   * Builds a skip response when proceed() was NOT called (e.g., cache hit).
   *
   * @param request the original request
   * @param context the context after callback execution
   * @param userResponse the user's response
   * @return the BEFORE phase response with shouldProceed=false
   * @throws IllegalStateException if skipProceed() was called without setReturnValue() or
   *     setExceptionToThrow()
   */
  private InterceptCallbackResponseMessage buildSkipResponse(
      InterceptCallbackRequestMessage request,
      InterceptContext context,
      InterceptCallbackResponse userResponse) {

    // Check for exception first (either from context or response)
    Throwable exceptionToThrow = context.getExceptionToThrow();
    if (exceptionToThrow == null) {
      exceptionToThrow = userResponse.getExceptionToThrow();
    }

    // Validate: skipProceed() requires either setReturnValue() or setExceptionToThrow()
    if (!context.isReturnValueModified() && exceptionToThrow == null) {
      throw new IllegalStateException(
          "skipProceed() was called but no return value was set. "
              + "You must call ctx.setReturnValue(value) or ctx.setExceptionToThrow(exception) "
              + "before skipping execution. Use ctx.setReturnValue(null) for explicit null.");
    }

    InterceptCallbackResponseMessage wireResponse = new InterceptCallbackResponseMessage();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(InterceptPhase.BEFORE.toByte());
    wireResponse.setShouldProceed(false);

    // Handle argument mutations (if any were made before deciding to skip)
    if (context.isArgsModified()) {
      wireResponse.setMutatedArgs(Wrapper.wrapArgsForceByValue(context.getArgsInternal()));
    }

    // Handle return value override (the value to return instead of executing)
    // Use getReturnValueInternal() because context is in BEFORE phase but user set a return value
    if (context.isReturnValueModified()) {
      wireResponse.setOverrideReturn(true);
      wireResponse.setNewReturnValue(Wrapper.wrapForceByValue(context.getReturnValueInternal()));
    }

    // Handle exception throwing
    if (exceptionToThrow != null) {
      wireResponse.setThrowException(true);
      wireResponse.setException(serializeException(exceptionToThrow));
    }

    return wireResponse;
  }

  /**
   * Builds an AFTER phase response when proceed() was called.
   *
   * @param request the original request
   * @param context the context after callback and proceed() execution
   * @param userResponse the user's response
   * @return the AFTER phase response
   */
  private InterceptCallbackResponseMessage buildAfterResponse(
      InterceptCallbackRequestMessage request,
      InterceptContext context,
      InterceptCallbackResponse userResponse) {
    InterceptCallbackResponseMessage wireResponse = new InterceptCallbackResponseMessage();
    wireResponse.setCallbackId(request.getCallbackId());
    wireResponse.setPhase(InterceptPhase.AFTER.toByte());

    // Handle return value override (post-proceed modifications)
    if (context.isReturnValueModified()) {
      wireResponse.setOverrideReturn(true);
      wireResponse.setNewReturnValue(Wrapper.wrapForceByValue(context.getReturnValue()));
    }

    // Handle exception throwing - check both context and response
    Throwable exceptionToThrow = context.getExceptionToThrow();
    if (exceptionToThrow == null) {
      exceptionToThrow = userResponse.getExceptionToThrow();
    }
    if (exceptionToThrow != null) {
      wireResponse.setThrowException(true);
      wireResponse.setException(serializeException(exceptionToThrow));
    }

    return wireResponse;
  }
}
