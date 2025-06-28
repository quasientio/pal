/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.core.objects.ObjectLookupStore;
import com.quasient.pal.core.objects.ObjectNotFoundException;
import com.quasient.pal.core.rpc.DispatcherConnector;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;

/**
 * Dispatcher for retrieving an instance variable's value using reflection.
 *
 * <p>This class extends the field dispatcher to support accessing instance fields on objects
 * referenced in RPC execution messages. It handles the retrieval of the target object and the
 * corresponding field from a class based on the provided message, optionally allowing non-public
 * field access based on configuration.
 */
@Singleton
public class GetInstanceVariableDispatcher extends GetFieldDispatcher {

  /**
   * Constructs a new dispatcher for instance variable retrieval.
   *
   * <p>Initializes the necessary components to locate and retrieve the requested field value from
   * an instance. This includes setting the peer identifier, the message builder for RPC
   * communications, a connector for dispatching messages, the access control flag for non-public
   * members, and the store used to lookup object references.
   *
   * @param peerUuid the unique identifier for the peer.
   * @param messageBuilder builder component used to create messages.
   * @param connector the connector used for message routing.
   * @param allowNonPublicAccess a flag (as a string) indicating whether non-public fields can be
   *     accessed.
   * @param objectLookupStore the store used to resolve object references to actual objects.
   */
  @Inject
  public GetInstanceVariableDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
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
   * <p>Returns the message type that should be sent before executing the field access operation.
   * For instance variable retrieval, this corresponds to {@code EXEC_GET_FIELD}.
   *
   * @return the message type indicating the pre-execution action.
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_GET_FIELD;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type used after the execution of the field access operation. For this
   * dispatcher, it returns {@code EXEC_RETURN_VALUE} to indicate that the result of the field
   * retrieval is being returned.
   *
   * @return the message type indicating the post-execution action.
   */
  @Override
  protected final MessageType getAfterExecMessageType() {
    return MessageType.EXEC_RETURN_VALUE;
  }

  /**
   * Retrieves the target object from the given execution message.
   *
   * <p>This method extracts an object reference from the message's instance field access
   * information, and uses the object lookup store to obtain the actual object. If the object
   * corresponding to the reference is not found, a NullPointerException is thrown with an
   * underlying {@code ObjectNotFoundException}.
   *
   * @param execMessage the execution message containing the instance field get details.
   * @return the target object from which the field will be accessed.
   * @throws NullPointerException if the object reference specified in the message cannot be
   *     resolved.
   */
  @Override
  protected Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    Object target;
    ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceFieldGet().getObjectRef());
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
   * Loads the accessible field object from the target class as specified in the execution message.
   *
   * <p>The method attempts to load the class based on the name provided in the message and then
   * looks for a public field matching the provided field name. If the public field is not found and
   * non-public access is permitted, it will attempt to retrieve a declared (possibly non-public)
   * field.
   *
   * @param execMessage the execution message containing the class and field information.
   * @param parameterTypes a list of parameter types; not used in this context.
   * @param args a list of arguments; not used in this context.
   * @return an {@link AccessibleObject} representing the located field.
   * @throws ReflectiveOperationException if the field cannot be located or accessed via reflection.
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class<?> clazz =
        Class.forName(
            execMessage.getInstanceFieldGet().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());

    final String fieldName = execMessage.getInstanceFieldGet().getField().getName();
    try {
      return clazz.getField(fieldName);
    } catch (NoSuchFieldException e) {
      if (allowNonPublicAccess) {
        return clazz.getDeclaredField(fieldName);
      }
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Specifies the type of message that this dispatcher handles. For instance variable retrieval,
   * it returns {@code EXEC_GET_FIELD}.
   *
   * @return the supported {@link MessageType} for instance field get operations.
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_GET_FIELD;
  }
}
