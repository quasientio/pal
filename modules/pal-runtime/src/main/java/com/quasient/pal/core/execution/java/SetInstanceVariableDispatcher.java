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
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.core.runtime.objects.ObjectNotFoundException;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Dispatcher that handles messages for setting instance variable values on objects.
 *
 * <p>This class is responsible for processing messages that request an instance field update. It
 * extracts the target object from the message, retrieves the appropriate accessible field, applies
 * the new value, and creates an acknowledgment message reflecting the operation result.
 */
@Singleton
public class SetInstanceVariableDispatcher extends SetFieldDispatcher {

  /**
   * Constructs a new dispatcher for handling instance variable modifications.
   *
   * <p>The dispatcher is initialized with necessary dependencies used to locate the target object,
   * build response messages, and manage field accessibility, including support for non-public field
   * access if enabled.
   *
   * @param peerUuid the unique identifier of the peer
   * @param messageBuilder the builder used to construct messages
   * @param connector the connector facilitating message routing
   * @param allowNonPublicAccess a flag indicating if non-public fields may be accessed
   * @param objectLookupStore the store used for resolving object references to instances
   */
  @Inject
  public SetInstanceVariableDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      OutboundMessageGateway connector,
      @Named("rpc.allow_nonpublic") String allowNonPublicAccess,
      ObjectLookupStore objectLookupStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setAllowNonPublicAccess(allowNonPublicAccess);
    setObjectLookupStore(objectLookupStore);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type representing the request to modify a field before executing the
   * update.
   *
   * @return the {@code EXEC_PUT_FIELD} message type
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_PUT_FIELD;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type representing the acknowledgment after the field update operation.
   *
   * @return the {@code EXEC_PUT_FIELD_DONE} message type
   */
  @Override
  protected final MessageType getAfterExecMessageType() {
    return MessageType.EXEC_PUT_FIELD_DONE;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts the target object for the field update from the provided execution message. It
   * retrieves the object reference and checks for its presence in the lookup store. If the target
   * is not found, a {@link NullPointerException} with the appropriate cause is thrown.
   *
   * @param execMessage the execution message containing instance field update details
   * @return the object instance whose field is to be updated
   * @throws NullPointerException if the object reference does not exist in the lookup store
   */
  @Override
  protected Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    Object target;
    ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef());
    if (objectLookupStore.containsObjectRef(targetObjRef)) {
      target = objectLookupStore.lookupObject(targetObjRef);
    } else {
      Exception objectNotFoundException =
          new ObjectNotFoundException(
              String.format("No object found with objRef: %d", targetObjRef.getRef()));
      NullPointerException npe = new NullPointerException(objectNotFoundException.getMessage());
      npe.initCause(objectNotFoundException);
      throw npe;
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Loaded target: {}", target);
    }
    return target;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Loads the accessible field corresponding to the instance variable update. It retrieves the
   * class and field names from the execution message and performs a reflective lookup.
   *
   * @param execMessage the execution message containing the field update information
   * @param parameterTypes the list of parameter types relevant for the lookup
   * @param args the list of argument values associated with the field operation
   * @return the {@link AccessibleObject} representing the target field
   * @throws ReflectiveOperationException if the field cannot be located or made accessible
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    return loadAccessibleObject(
        execMessage.getInstanceFieldPut().getClazz().getName(),
        execMessage.getInstanceFieldPut().getField().getName(),
        parameterTypes,
        args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts the new field value from the execution message. The value may be provided directly
   * or as a reference, and this method handles both scenarios to produce the value to be set in the
   * target field.
   *
   * @param execMessage the execution message carrying the update payload
   * @param accessibleObject the accessible field that is being updated, which may influence value
   *     interpretation
   * @return the new value to assign to the field, or {@code null} if no valid value is provided
   */
  @Override
  protected @Nullable Object getValueFromMessage(
      final ExecMessage execMessage, final AccessibleObject accessibleObject) {
    return getValueFromMessage(
        execMessage.getInstanceFieldPut().getValueObject(),
        execMessage.getInstanceFieldPut().getValueObjectRef(),
        accessibleObject);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs the follow-up execution message that reflects the outcome of the field update. In
   * the event of errors during field resolution or the update itself, the returned message wraps
   * the encountered exceptions; otherwise, it confirms the successful update of the object.
   *
   * @param execMessage the original execution message
   * @param valueObject the object representing the new field value
   * @param valueObjRef the reference associated with the value object, if applicable
   * @param accessibleObject the accessible field object that was updated
   * @param exceptionWhileLoading an exception encountered during field loading, or {@code null} if
   *     none
   * @param exceptionWhileInvoking an exception encountered during the update operation, or {@code
   *     null} if none
   * @return an {@link ExecMessage} conveying the result of the field update, including any error
   *     details
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
    return messageBuilder.buildPutObjectDone(peerUuid, accessibleObject, messageId, messageId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Specifies the message type supported by this dispatcher for instance field updates.
   *
   * @return the {@code EXEC_PUT_FIELD} message type supported by this dispatcher
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_PUT_FIELD;
  }
}
