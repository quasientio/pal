/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.core.rpc.exec.java;

import com.quasient.pal.common.objects.ObjectLookupStore;
import com.quasient.pal.common.objects.ObjectRef;
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
import javax.annotation.Nullable;

/**
 * Dispatcher implementation for handling requests that update static class variables.
 *
 * <p>This class extends {@link SetFieldDispatcher} and utilizes reflection to locate and modify
 * static fields. It is responsible for building and dispatching both pre-execution and
 * post-execution messages associated with a static field update operation.
 */
@Singleton
public class SetClassVariableDispatcher extends SetFieldDispatcher {

  /**
   * Constructs a new dispatcher for processing static field update requests.
   *
   * <p>The dispatcher is initialized with the necessary components for message construction,
   * routing, access control, and object lookup. Dependencies are injected via this constructor.
   *
   * @param peerUuid the unique identifier for the peer.
   * @param messageBuilder the {@link MessageBuilder} used to construct messages.
   * @param connector the {@link DispatcherConnector} that facilitates message routing.
   * @param allowNonPublicAccess a configuration flag indicating whether non-public members may be
   *     accessed.
   * @param objectLookupStore the store used for retrieving objects via {@link ObjectRef}
   *     references.
   */
  @Inject
  public SetClassVariableDispatcher(
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
   * <p>Returns the message type used to signal the beginning of a static field update operation.
   *
   * @return {@link MessageType#EXEC_PUT_STATIC} used before executing the update.
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_PUT_STATIC;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type used to indicate the completion of a static field update operation.
   *
   * @return {@link MessageType#EXEC_PUT_STATIC_DONE} used after executing the update.
   */
  @Override
  protected final MessageType getAfterExecMessageType() {
    return MessageType.EXEC_PUT_STATIC_DONE;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Loads the accessible object corresponding to the static field specified in the execution
   * message. This method extracts the fully qualified class name and field name from the message
   * and uses reflection to retrieve the corresponding field.
   *
   * @param execMessage the execution message containing details of the static field to update.
   * @param parameterTypes the list of parameter types relevant if a setter method is involved.
   * @param args the arguments corresponding to the parameter types.
   * @return an {@link AccessibleObject} representing the target static field.
   * @throws ReflectiveOperationException if the field cannot be accessed or resolved via
   *     reflection.
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    return loadAccessibleObject(
        execMessage.getStaticFieldPut().getClazz().getName(),
        execMessage.getStaticFieldPut().getField().getName(),
        parameterTypes,
        args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts the value to be applied to the static field from the execution message. Depending
   * on the message content, the value is retrieved either directly or via a reference object.
   *
   * @param execMessage the execution message containing the update details.
   * @param accessibleObject the reflective object representing the target static field.
   * @return the value to be assigned to the static field, or {@code null} if no value is provided.
   */
  @Override
  protected @Nullable Object getValueFromMessage(
      final ExecMessage execMessage, final AccessibleObject accessibleObject) {
    return getValueFromMessage(
        execMessage.getStaticFieldPut().getValueObject(),
        execMessage.getStaticFieldPut().getValueObjectRef(),
        accessibleObject);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs the response message after an attempt to update a static field. If an exception
   * was encountered during field lookup or invocation, this method returns an error message.
   * Otherwise, it constructs a successful response message indicating the completion of the update.
   *
   * @param execMessage the original execution message for the static field update.
   * @param valueObject the value used in the update operation.
   * @param valueObjRef the object reference wrapper associated with the value.
   * @param accessibleObject the accessible field that was targeted.
   * @param exceptionWhileLoading any exception encountered during field lookup, or {@code null} if
   *     none.
   * @param exceptionWhileInvoking any exception encountered during field update, or {@code null} if
   *     none.
   * @return the {@link ExecMessage} representing the outcome of the static field update.
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
    return messageBuilder.buildPutStaticDone(peerUuid, accessibleObject, messageId, messageId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Specifies the type of exec message that this dispatcher supports.
   *
   * @return {@link MessageType#EXEC_PUT_STATIC}, indicating that the dispatcher handles static
   *     field update requests.
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_PUT_STATIC;
  }
}
