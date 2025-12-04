/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import com.quasient.pal.common.lang.reflect.MethodSignature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Provides reflection-based dispatching of method calls.
 *
 * <p>This abstract class adapts deserialized parameters to the method's expected signature,
 * performs the reflective invocation on a given target, and constructs the appropriate
 * post-execution message, handling both normal return values and execution exceptions.
 */
public abstract class MethodDispatcher extends BaseExecMessageDispatcher {

  /**
   * {@inheritDoc}
   *
   * <p>This implementation ignores the provided {@code value} parameter and delegates the method
   * invocation to a specialized private handler.
   *
   * @param accessibleObject the reflective method object representing the method to be invoked.
   * @param target the instance on which the method is to be executed.
   * @param args the list of message arguments to be adapted and supplied to the method.
   * @param value an additional parameter that is deliberately ignored.
   * @return the result produced by the invoked method.
   * @throws Exception if an error occurs during parameter adaptation or method invocation.
   */
  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws Exception {
    // discard value
    return invokeIncoming(accessibleObject, target, args);
  }

  /**
   * Invokes the target method using the provided accessible object and adapted parameters.
   *
   * <p>The method first logs the invocation details if trace logging is enabled, then adapts the
   * supplied deserialized message arguments to match the method's parameter types before performing
   * the reflective invocation.
   *
   * @param accessibleObject the reflective representation of the method to be invoked (must be a
   *     {@link Method}).
   * @param target the object instance on which the method is invoked.
   * @param deserializedArgs the list of message arguments to be converted into the method's
   *     parameter types.
   * @return the result returned from the method invocation.
   * @throws Exception if parameter adaptation fails or if an exception is thrown during method
   *     invocation.
   */
  private Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> deserializedArgs)
      throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}, args: {}",
          ((Method) accessibleObject).toGenericString(),
          target,
          deserializedArgs);
    }
    Method method = (Method) accessibleObject;
    Object[] args =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method, deserializedArgs.toArray(new MessageArgument[0]));
    return method.invoke(target, args);
  }

  @Override
  protected ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject method = ((MethodSignature) ctxt.getSignature()).getMethod();

    if (value instanceof InvocationThrowableWrapper throwableWrapper) {
      Throwable invocationThr = throwableWrapper.throwable();
      return messageBuilder.buildAccessibleObjectThrowableEphemeral(method, invocationThr, null);
    } else {
      return messageBuilder.buildReturnValueEphemeral(value, method, objectRef, isVoid, null);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs a post-execution message based on the outcome of the method call. If an exception
   * occurred during method loading or invocation, the returned message wraps the encountered
   * throwable; otherwise, a normal return value message is constructed.
   *
   * @param execMessage the original execution message containing context for the call.
   * @param valueObject the object returned by the method invocation.
   * @param valueObjRef a reference wrapper for the returned value.
   * @param accessibleObject the reflective method object associated with the executed call.
   * @param exceptionWhileLoading an exception thrown during the method loading phase, if any.
   * @param exceptionWhileInvoking an exception thrown during the method invocation phase, if any.
   * @return an {@link ExecMessage} representing the outcome of the execution, either wrapping an
   *     exception or containing the return value.
   */
  @Override
  protected ExecMessage createAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    String messageId = execMessage.getMessageId();

    if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
      return wrapAfterExecThrowableMessage(
          messageId, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
    }

    return messageBuilder.buildReturnValue(
        valueObject, accessibleObject, valueObjRef, returnsVoid(accessibleObject), messageId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Determines whether the method associated with the provided accessible object declares a void
   * return type.
   *
   * @param accessibleObject the reflective method object to be evaluated (must be a {@link
   *     Method}).
   * @return {@code true} if the method's return type is void; {@code false} otherwise.
   */
  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return ((Method) accessibleObject).getReturnType().equals(Void.TYPE);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts the return type from the AspectJ method signature to determine if the method
   * returns void.
   *
   * @param pjp the proceeding join point representing the intercepted method call
   * @return {@code true} if the method's return type is void; {@code false} otherwise
   */
  @Override
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  protected boolean returnsVoid(ProceedingJoinPoint pjp) {
    // Using FQN because there's a name conflict with
    // com.quasient.pal.common.lang.reflect.MethodSignature
    org.aspectj.lang.reflect.MethodSignature sig =
        (org.aspectj.lang.reflect.MethodSignature) pjp.getSignature();
    return sig.getReturnType().equals(Void.TYPE);
  }
}
