/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dispatcher responsible for handling requests for accessing static fields (class variables). This
 * class extends the field dispatcher functionality by specifically targeting class-level (static)
 * variables and determining the appropriate reflective field access.
 */
@Singleton
public class GetClassVariableDispatcher extends GetFieldDispatcher {

  /**
   * Constructs a dispatcher for retrieving static field values from a class.
   *
   * <p>This constructor configures the dispatcher with necessary parameters including identifiers,
   * message serialization tools, connection details, and lookup store.
   *
   * @param peerUuid a unique identifier for the peer
   * @param runOptions the run options governing enabled features
   * @param messageBuilder the builder responsible for constructing execution messages
   * @param gateway instance of {@link OutboundMessageGateway} handling message routing
   * @param objectLookupStore a store for tracking and retrieving objects by identifier
   */
  @Inject
  public GetClassVariableDispatcher(
      UUID peerUuid,
      Set<RunOptions> runOptions,
      MessageBuilder messageBuilder,
      OutboundMessageGateway gateway,
      ObjectLookupStore objectLookupStore) {
    setPeerUuid(peerUuid);
    setRunOptions(runOptions);
    setMessageBuilder(messageBuilder);
    setMessageGateway(gateway);
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
   * provided name. If not found, it falls back to the declared (possibly non-public) field. RPC
   * access control is enforced earlier in the dispatch path by the RPC policy checker.
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
      return clazz.getDeclaredField(fieldName);
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
