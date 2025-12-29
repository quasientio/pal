/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Dispatches field operation execution messages within the PAL runtime.
 *
 * <p>This abstract class provides a framework for constructing execution messages associated with
 * field operations. It implements the pre- and post-execution messaging logic, handling both normal
 * operation results and exceptions. Subclasses are expected to define specific behaviors regarding
 * the return type of the field operation and post-execution messaging.
 */
public abstract class FieldOpDispatcher extends BaseExecMessageDispatcher {

  /**
   * {@inheritDoc}
   *
   * <p>Constructs an execution message representing the pre-execution state of a field operation.
   * This implementation selects the primary parameter from the provided arguments (if any) and
   * builds a field operation message using the designated message type.
   *
   * @param ctxt the execution context containing relevant state and signature information.
   * @param sender the object initiating the field operation.
   * @param target the object on which the field operation is performed.
   * @param args arguments passed to the operation; the first element is used as the key parameter
   *     if available.
   * @return an {@link ExecMessage} representing the state before executing the field operation.
   */
  @Override
  protected final ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    Object parameter = (args == null || args.length == 0) ? null : args[0];
    return messageBuilder.buildFieldOpEphemeral(
        ctxt,
        getBeforeExecMessageType(),
        sender,
        generateObjectRef(sender),
        generateObjectRef(target),
        parameter,
        generateObjectRef(parameter));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs an execution message representing the result of a field operation. Depending on
   * the outcome of the operation, if the value encapsulates an invocation exception, the message
   * will wrap the encountered exception. Otherwise, it will either include the returned value or
   * indicate completion if the operation is void.
   *
   * @param ctxt the execution context with the current operation's details.
   * @param value the result of the field operation, or an exception wrapper in case of invocation
   *     failure.
   * @param objectRef a reference to the object involved in the operation for further processing.
   * @param isVoid a flag indicating whether the operation is expected to return a value (ignored in
   *     favor of {@link #returnsVoid()}).
   * @return an {@link ExecMessage} encapsulating the outcome of the field operation.
   */
  @Override
  protected final ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    AccessibleObject field = ((FieldSignature) ctxt.getSignature()).getField();

    if (value instanceof InvocationThrowableWrapper throwableWrapper) {
      Throwable invocationThr = throwableWrapper.throwable();
      return messageBuilder.buildAccessibleObjectThrowableEphemeral(field, invocationThr, null);
    } else {
      if (!returnsVoid()) {
        return messageBuilder.buildReturnValueEphemeral(value, field, objectRef, false, null);
      } else {
        return messageBuilder.buildFieldOpDoneEphemeral(field, ctxt, getAfterExecMessageType());
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Retrieves the parameter list associated with the given execution message. This dispatcher
   * does not rely on a parameter list for field operations, hence it always returns null.
   *
   * @param execMessage the execution message for which the parameter list is requested.
   * @return always null, indicating that no parameters are used for field operations.
   */
  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return null;
  }

  /**
   * Determines whether the field operation is expected to return void.
   *
   * <p>This abstract method should be implemented by subclasses to indicate if the field operation
   * produces a return value or if it is solely intended to perform an action without returning
   * data.
   *
   * @return true if the field operation returns void; false if a return value is expected.
   */
  protected abstract boolean returnsVoid();

  /**
   * Returns the message type to be used for post-execution messaging of the field operation.
   *
   * <p>Subclasses should implement this method to specify a concrete {@link MessageType} that
   * aligns with the nature of the field operation result.
   *
   * @return the {@link MessageType} for the post-execution phase of the field operation.
   */
  protected abstract MessageType getAfterExecMessageType();

  /**
   * {@inheritDoc}
   *
   * <p>For field operations, the return type is fixed: get operations return a value, set
   * operations are void. This method delegates to the no-arg {@link #returnsVoid()} method.
   *
   * @param pjp the proceeding join point (unused for field operations)
   * @return true if this is a set field operation; false if this is a get field operation
   */
  @Override
  protected boolean returnsVoid(ProceedingJoinPoint pjp) {
    return returnsVoid();
  }
}
