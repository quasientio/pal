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
import com.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.core.runtime.objects.ObjectNotFoundException;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Dispatcher for invoking instance method calls via reflection or a {@link ProceedingJoinPoint}.
 *
 * <p>It is responsible for constructing execution messages before and after the method invocation,
 * invoking the target method on instance objects, and handling any exceptions that occur during the
 * call. The dispatcher integrates with the object lookup store and reflection helper to resolve and
 * execute instance methods.
 */
@Singleton
public class InstanceMethodDispatcher extends MethodDispatcher {

  /**
   * Constructs a new InstanceMethodDispatcher configured to dispatch instance method calls.
   *
   * @param peerUuid a unique identifier representing the peer.
   * @param runOptions the run options governing enabled features
   * @param messageBuilder a builder for constructing execution messages.
   * @param gateway the gateway used for message routing.
   * @param allowNonPublicAccess string flag controlling non-public method access (e.g., "true" or
   *     "false").
   * @param reflectionHelper helper for reflective operations to resolve target methods.
   * @param objectLookupStore store for object references used during target object lookup.
   */
  @Inject
  public InstanceMethodDispatcher(
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
   * <p>Constructs an execution message representing the pre-invocation state for an instance method
   * call. This implementation builds a message incorporating the execution context, sender and
   * target object references, original arguments, and their corresponding object references.
   *
   * @param ctxt the execution context containing method signature and related metadata.
   * @param sender the object initiating the method call.
   * @param target the target object on which the method will be invoked.
   * @param args the array of arguments to be passed to the target method.
   * @return an ExecMessage instance encapsulating pre-execution invocation details.
   */
  @Override
  protected final ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    return messageBuilder.buildInstanceMethodMessageEphemeral(
        ctxt,
        sender,
        generateObjectRef(sender),
        generateObjectRef(target),
        args,
        Arrays.stream(args).map(this::generateObjectRef).toArray(ObjectRef[]::new));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs an execution message representing the post-invocation outcome of an instance
   * method call. Depending on whether the invocation was successful or resulted in an exception,
   * this method builds an appropriate message encapsulating either the method's return value or the
   * encountered exception.
   *
   * @param ctxt the execution context carrying method signature information.
   * @param value the result of the method invocation, or an InvocationThrowableWrapper in case of
   *     error.
   * @param objectRef the object reference associated with the target, used for object management.
   * @param isVoid flag indicating whether the method's return type is void.
   * @return an ExecMessage representing either the method's return value or an invocation error.
   */
  @Override
  protected ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject method = ((MethodSignature) ctxt.getSignature()).getMethod();

    if (value instanceof InvocationThrowableWrapper) {
      Throwable invocationThr = ((InvocationThrowableWrapper) value).throwable();
      return messageBuilder.buildAccessibleObjectThrowableEphemeral(method, invocationThr, null);
    } else {
      return messageBuilder.buildReturnValueEphemeral(value, method, objectRef, isVoid, null);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type corresponding to a pre-execution instance method call.
   *
   * @return MessageType.EXEC_INSTANCE_METHOD indicating the type of message used for instance
   *     method execution.
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_INSTANCE_METHOD;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts and returns the list of parameters from an instance method execution message.
   *
   * @param execMessage the execution message containing the method call details.
   * @return a list of Parameter objects representing the method call's parameters.
   */
  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getInstanceMethodCall().getParameters());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Retrieves the target object for an instance method call based on the provided execution
   * message. The method looks up the target object using its object reference from a lookup store.
   * If no matching object is found, a NullPointerException is thrown with a detailed cause.
   *
   * @param execMessage the execution message containing target object reference details.
   * @return the target object to be used for method invocation.
   * @throws NullPointerException if no object corresponding to the provided reference is found.
   */
  @Override
  protected Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    if (logger.isTraceEnabled()) {
      if (execMessage.getInstanceMethodCall().getObjectRef() != null
          && !execMessage.getInstanceMethodCall().getObjectRef().isEmpty()) {
        logger.trace("ObjectRef: {}", execMessage.getInstanceMethodCall().getObjectRef());
      }
    }

    Object target;
    ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceMethodCall().getObjectRef());
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
   * <p>Loads and returns the accessible method matching the instance method call described in the
   * execution message. The method uses reflection to load the target class and leverages a helper
   * to resolve the correct method based on provided parameter types and arguments, assisting in
   * handling overloaded methods.
   *
   * @param execMessage the execution message with details of the method call.
   * @param parameterTypes list of expected parameter types used for method resolution.
   * @param args the list of arguments to be passed to the target method.
   * @return the AccessibleObject representing the resolved method.
   * @throws ReflectiveOperationException if the class cannot be loaded or the method cannot be
   *     accessed.
   * @throws AmbiguousCallException if multiple matching methods are found, leading to an ambiguous
   *     call.
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException, AmbiguousCallException {
    Class<?> clazz =
        Class.forName(
            execMessage.getInstanceMethodCall().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return reflectionHelper.lookupMethod(
        clazz, args.toArray(), parameterTypes, execMessage.getInstanceMethodCall().getName());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the supported message type for this dispatcher. This dispatcher specifically handles
   * messages corresponding to instance method executions.
   *
   * @return MessageType.EXEC_INSTANCE_METHOD for instance method call messages.
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_INSTANCE_METHOD;
  }
}
