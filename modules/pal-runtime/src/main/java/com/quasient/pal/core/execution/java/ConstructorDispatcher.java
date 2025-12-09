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

import static java.lang.Class.forName;

import com.quasient.pal.common.lang.reflect.ConstructorSignature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Dispatcher responsible for handling execution messages for constructor calls.
 *
 * <p>This class utilizes either a {@link ProceedingJoinPoint} or reflection to look up and invoke
 * constructors based on incoming execution messages. It builds pre- and post-execution messages to
 * encapsulate the invocation details and any exceptions that may occur.
 */
@SuppressFBWarnings(
    value = "BC_UNCONFIRMED_CAST",
    justification = "Type already validated before cast in dispatcher pattern")
@Singleton
public class ConstructorDispatcher extends BaseExecMessageDispatcher {

  /**
   * Constructs a new dispatcher for handling constructor invocations.
   *
   * @param peerUuid the unique identifier for the peer invoking the constructor
   * @param runOptions the run options governing enabled features
   * @param messageBuilder builder used to create execution messages for dispatching
   * @param gateway instance of {@link OutboundMessageGateway} used to facilitate message transport
   * @param allowNonPublicAccess flag indicating whether non-public constructors may be accessed
   * @param reflectionHelper helper utility for performing reflection-based lookups
   * @param objectLookupStore store used for managing object references during dispatch
   */
  @Inject
  public ConstructorDispatcher(
      UUID peerUuid,
      Set<RunOptions> runOptions,
      MessageBuilder messageBuilder,
      OutboundMessageGateway gateway,
      @Named("rpc.allow_nonpublic") String allowNonPublicAccess,
      ReflectionHelper reflectionHelper,
      ObjectLookupStore objectLookupStore) {
    setPeerUuid(peerUuid);
    setRunOptions(runOptions);
    setMessageBuilder(messageBuilder);
    setMessageGateway(gateway);
    setAllowNonPublicAccess(allowNonPublicAccess);
    setReflectionHelper(reflectionHelper);
    setObjectLookupStore(objectLookupStore);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a pre-execution execution message for a constructor call by storing sender and
   * argument references.
   *
   * @param ctxt the current execution context containing invocation data
   * @param sender the object initiating the constructor call
   * @param target the target on which the constructor is eventually invoked (null and ignored in
   *     constructor calls)
   * @param args the arguments to be passed to the constructor
   * @return an {@link ExecMessage} representing the constructor call request
   */
  @Override
  protected final ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {

    return messageBuilder.buildConstructorMessageEphemeral(
        ctxt,
        sender,
        generateObjectRef(sender),
        args,
        Arrays.stream(args).map(this::generateObjectRef).toArray(ObjectRef[]::new));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a post-execution message after the constructor call. If the invocation resulted in
   * an exception, the exception is wrapped into a throwable message; otherwise, the return value
   * message is built.
   *
   * @param ctxt the current execution context containing the constructor signature
   * @param value the object returned from the constructor or an exception wrapper if invocation
   *     failed
   * @param objectRef the reference to the constructed object
   * @param isVoid flag indicating whether the method should be treated as void (always false for
   *     constructors)
   * @return an {@link ExecMessage} representing the outcome of the constructor invocation
   */
  @Override
  protected final ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject constructor =
        ((ConstructorSignature) ctxt.getSignature()).getConstructor();

    if (value instanceof InvocationThrowableWrapper throwableWrapper) {
      Throwable invocationThr = throwableWrapper.throwable();
      return messageBuilder.buildAccessibleObjectThrowableEphemeral(
          constructor, invocationThr, null);
    } else {
      return messageBuilder.buildReturnValueEphemeral(value, constructor, objectRef, false, null);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a post-execution message based on the provided execution message and the outcome of
   * the constructor invocation. If exceptions occurred during loading or invoking the constructor,
   * creates an appropriate throwable message.
   *
   * @param execMessage the original execution message associated with this call
   * @param valueObject the resultant object produced by the constructor
   * @param valueObjRef reference to the constructed object
   * @param accessibleObject the reflective constructor object used in the call
   * @param exceptionWhileLoading exception encountered during the loading phase, if any
   * @param exceptionWhileInvoking exception encountered during the invocation phase, if any
   * @return an {@link ExecMessage} encapsulating the result or error state after invoking the
   *     constructor
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
        valueObject, accessibleObject, valueObjRef, false, messageId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Processes an incoming message for a constructor call. In this implementation, the target
   * object and the provided value are discarded, and the invocation is handled solely based on the
   * accessible object and deserialized arguments.
   *
   * @param accessibleObject the accessible constructor object to be used for invocation
   * @param target the target object (ignored in constructor invocations)
   * @param args list of message arguments to be adapted for the constructor call
   * @param value a pre-provided value (ignored in this context)
   * @return the object created by invoking the constructor with adapted arguments
   * @throws ReflectiveOperationException if any error occurs during argument adaptation or
   *     constructor invocation
   */
  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws ReflectiveOperationException {
    // discard target and value
    return invokeIncoming(accessibleObject, args);
  }

  /**
   * Invokes the constructor represented by the given accessible object using the provided
   * deserialized arguments.
   *
   * <p>Adapts the incoming message arguments to match the constructor's parameter types using a
   * utility method, then creates a new instance by invoking the constructor.
   *
   * @param accessibleObject the reflective constructor to be invoked
   * @param deserializedArgs the list of arguments deserialized from the incoming message
   * @return a new object instance created by the constructor
   * @throws ReflectiveOperationException if instantiation fails or arguments are incompatible with
   *     the constructor
   */
  private Object invokeIncoming(
      AccessibleObject accessibleObject, List<MessageArgument> deserializedArgs)
      throws ReflectiveOperationException {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, args: {}",
          accessibleObject,
          deserializedArgs);
    }
    Constructor<?> constructor = (Constructor<?>) accessibleObject;
    Object[] args =
        ParameterAdaptationUtils.adaptParametersForConstructor(
            constructor, deserializedArgs.toArray(new MessageArgument[0]));
    return constructor.newInstance(args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Indicates that a constructor invocation never returns void.
   *
   * @param accessibleObject the accessible constructor (unused in this implementation)
   * @return always {@code false} since constructors yield object instances
   */
  @Override
  protected final boolean returnsVoid(AccessibleObject accessibleObject) {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Indicates that a constructor invocation never returns void.
   *
   * @param pjp the proceeding join point (unused for constructor operations)
   * @return always {@code false} since constructors yield object instances
   */
  @Override
  protected boolean returnsVoid(ProceedingJoinPoint pjp) {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type used for pre-execution messages specific to constructor calls.
   *
   * @return {@link MessageType#EXEC_CONSTRUCTOR} indicating the message type for constructor
   *     execution
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_CONSTRUCTOR;
  }

  /**
   * Retrieves the list of parameters from the constructor call encapsulated within the given
   * execution message.
   *
   * @param execMessage the execution message containing the constructor call details
   * @return a list of {@link Parameter} objects representing the constructor parameters
   */
  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getConstructorCall().getParameters());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Loads the constructor reflective object based on the provided execution message, parameter
   * types, and arguments. It uses dynamic class loading and a helper to accurately match the
   * constructor signature.
   *
   * @param execMessage the execution message containing the target class and constructor details
   * @param parameterTypes the list of parameter types expected by the constructor
   * @param args the list of arguments to be passed to the constructor
   * @return the {@link AccessibleObject} representing the located constructor
   * @throws ReflectiveOperationException if the target class or constructor cannot be found
   * @throws AmbiguousCallException if multiple matching constructors are encountered
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException, AmbiguousCallException {
    Class<?> clazz =
        forName(
            execMessage.getConstructorCall().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return reflectionHelper.lookupConstructor(clazz, args.toArray(), parameterTypes);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the supported message type for this dispatcher, which handles constructor execution
   * calls.
   *
   * @return {@link MessageType#EXEC_CONSTRUCTOR} representing the supported execution message type
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_CONSTRUCTOR;
  }
}
