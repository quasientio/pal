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

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Abstract dispatcher for executing field operations via reflection or a {@link
 * ProceedingJoinPoint}
 *
 * <p>This abstract class extends the field operation dispatcher to specifically handle operations
 * that retrieve the value of a field via Java reflection or a JoinPoint's proceed(). It implements
 * both the invocation of the field access and the creation of messages that encapsulate the result
 * or exceptions from such operations.
 */
public abstract class GetFieldDispatcher extends FieldOpDispatcher {

  /**
   * Processes an incoming reflective get field request by extracting the field value.
   *
   * <p>This method discards the provided additional arguments and value, delegating the retrieval
   * of the field value to a dedicated helper method.
   *
   * @param accessibleObject the accessible object (expected to be a {@code Field}) representing the
   *     field to retrieve.
   * @param target the object instance from which the field value is to be retrieved.
   * @param args a list of message arguments (ignored in this request processing).
   * @param value an additional value parameter (ignored in this operation).
   * @return the value of the specified field obtained from the target object.
   * @throws Exception if an error occurs during the reflective field access.
   */
  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws Exception {

    // discard args and value
    return invokeIncoming(accessibleObject, target);
  }

  /**
   * Retrieves the field value from the target object using reflection.
   *
   * <p>This helper method casts the provided accessible object to a {@code Field}, logs the access
   * if trace logging is enabled, and returns the field's value.
   *
   * @param accessibleObject the accessible object representing the {@code Field}.
   * @param target the object instance from which to extract the field value.
   * @return the value of the field obtained from the target object.
   * @throws Exception if an error occurs during the field access operation.
   */
  private Object invokeIncoming(AccessibleObject accessibleObject, Object target) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}", accessibleObject, target);
    }
    Field field = (Field) accessibleObject;
    return field.get(target);
  }

  /**
   * Creates the execution message to be sent after processing the get field operation.
   *
   * <p>Depending on whether exceptions were encountered during field loading or invocation, this
   * method either wraps the exception details into a message or constructs a message containing the
   * retrieved field's value.
   *
   * @param execMessage the original execution message containing the message identifier.
   * @param valueObject the value retrieved from the field operation.
   * @param valueObjRef a reference encapsulating the field value.
   * @param accessibleObject the accessible object representing the field.
   * @param exceptionWhileLoading any exception encountered during field loading, if applicable.
   * @param exceptionWhileInvoking any exception encountered during field invocation, if applicable.
   * @return an {@code ExecMessage} containing either the result of the operation or the exception
   *     details.
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
        peerUuid, valueObject, accessibleObject, valueObjRef, returnsVoid(), messageId);
  }

  /**
   * Indicates that the get field operation is expected to return a value.
   *
   * @return {@code false} since the operation produces a non-void result.
   */
  @Override
  protected final boolean returnsVoid() {
    return false;
  }

  /**
   * Determines that the get field operation associated with the provided accessible object returns
   * a value.
   *
   * @param accessibleObject the accessible object representing the field.
   * @return {@code false} as get field operations always produce a non-void result.
   */
  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return false;
  }
}
