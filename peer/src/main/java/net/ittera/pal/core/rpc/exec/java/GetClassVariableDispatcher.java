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

package net.ittera.pal.core.rpc.exec.java;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.core.rpc.DispatcherConnector;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;

/**
 * Dispatcher responsible for handling requests for accessing static fields (class variables). This
 * class extends the field dispatcher functionality by specifically targeting class-level (static)
 * variables and determining the appropriate reflective field access, considering the configuration
 * for non-public access.
 */
@Singleton
public class GetClassVariableDispatcher extends GetFieldDispatcher {

  /**
   * Constructs a dispatcher for retrieving static field values from a class.
   *
   * <p>This constructor configures the dispatcher with necessary parameters including identifiers,
   * message serialization tools, connection details, and lookup store. The {@code
   * allowNonPublicAccess} parameter indicates whether attempts to access non-public fields are
   * permitted if a public field is unavailable.
   *
   * @param peerUuid a unique identifier for the peer
   * @param messageBuilder the builder responsible for constructing execution messages
   * @param connector the dispatcher connector handling message routing
   * @param allowNonPublicAccess a configuration flag (as String) indicating if non-public fields
   *     may be accessed
   * @param objectLookupStore a store for tracking and retrieving objects by identifier
   */
  @Inject
  public GetClassVariableDispatcher(
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
   * <p>Returns the message type used to signal the beginning of a static field retrieval operation.
   *
   * @return {@link MessageType#EXEC_GET_STATIC} indicating a static field access request
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_GET_STATIC;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type that indicates the response containing the value of a retrieved
   * static field.
   *
   * @return {@link MessageType#EXEC_RETURN_VALUE} representing the response after execution
   */
  @Override
  protected final MessageType getAfterExecMessageType() {
    return MessageType.EXEC_RETURN_VALUE;
  }

  /**
   * Retrieves a reflective {@link AccessibleObject} representing a static field specified in the
   * execution message.
   *
   * <p>The method attempts to load the class designated in the {@code execMessage} using the
   * current thread's context class loader. It then tries to retrieve the public field with the
   * provided name. If the public field is not found and non-public access is enabled, the method
   * returns the declared (possibly non-public) field. Otherwise, it propagates the encountered
   * {@link NoSuchFieldException}.
   *
   * @param execMessage the execution message containing details of the static field operation
   * @param parameterTypes the list of expected parameter types (required by the API, though unused
   *     in this context)
   * @param args the list of arguments provided for the operation (not used in this implementation)
   * @return the accessible field object corresponding to the requested static field
   * @throws ReflectiveOperationException if the field cannot be found or accessed under the current
   *     configuration
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class<?> clazz =
        Class.forName(
            execMessage.getStaticFieldGet().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    final String fieldName = execMessage.getStaticFieldGet().getField().getName();
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
   * <p>Specifies that this dispatcher supports operations related to retrieving static fields.
   *
   * @return {@link MessageType#EXEC_GET_STATIC} identifying the supported message type for this
   *     dispatcher
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_GET_STATIC;
  }
}
