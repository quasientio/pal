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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Dispatcher for invoking static class methods via reflection or a {@link ProceedingJoinPoint}
 *
 * <p>This dispatcher prepares and dispatches messages corresponding to class method executions. It
 * builds both pre- and post-execution messages and handles invocation via reflection or a
 * JoinPoint's proceed() invocation, including proper handling of exceptions through wrapping.
 *
 * @see MethodDispatcher
 */
@Singleton
public class ClassMethodDispatcher extends MethodDispatcher {

  /**
   * Constructs a dispatcher for static class methods.
   *
   * <p>Injected dependencies support message building, reflective method lookup, and object
   * reference management required for dispatching method calls.
   *
   * @param peerUuid a unique identifier representing the messaging peer.
   * @param runOptions the run options governing enabled features
   * @param messageBuilder the builder to construct messages associated with method dispatch.
   * @param gateway the dispatcher gateway to facilitate message transport.
   * @param allowNonPublicAccess string indicating if non-public members can be accessed.
   * @param reflectionHelper helper for method lookups.
   * @param objectLookupStore store for object references used in method invocations.
   */
  @Inject
  public ClassMethodDispatcher(
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
   * <p>Constructs a message that represents the invocation of a static class method before its
   * execution. It transforms the sender and each argument into their corresponding object
   * references.
   *
   * @param ctxt the execution context holding method signature and other invocation details.
   * @param sender the object initiating the method call.
   * @param target the target object (unused for static methods).
   * @param args the arguments to pass to the method.
   * @return an {@link ExecMessage} representing the pre-execution state for the class method call.
   */
  @Override
  protected final ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    return messageBuilder.buildClassMethod(
        peerUuid,
        ctxt,
        sender,
        generateObjectRef(sender),
        args,
        Arrays.stream(args).map(this::generateObjectRef).toArray(ObjectRef[]::new));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Constructs a post-execution message based on the outcome of the class method invocation. If
   * the invocation resulted in an exception, returns a message encapsulating the throwable;
   * otherwise, returns a message with the method's return value.
   *
   * @param ctxt the execution context with method details.
   * @param value the result of the method invocation, or a wrapped exception.
   * @param objectRef the reference to the target object.
   * @param isVoid indicates whether the method returns no value.
   * @return an {@link ExecMessage} representing the post-execution state.
   */
  @Override
  protected ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject method = ((MethodSignature) ctxt.getSignature()).getMethod();

    if (value instanceof InvocationThrowableWrapper) {
      Throwable invocationThr = ((InvocationThrowableWrapper) value).throwable();
      return messageBuilder.buildAccessibleObjectThrowable(peerUuid, method, invocationThr, null);
    } else {
      return messageBuilder.buildReturnValue(peerUuid, value, method, objectRef, isVoid, null);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the message type representing a class method invocation pre-execution.
   *
   * @return {@link MessageType#EXEC_CLASS_METHOD} that identifies this invocation type.
   */
  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_CLASS_METHOD;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Extracts the list of parameters from the class method call portion of the given execution
   * message.
   *
   * @param execMessage the execution message containing class method call details.
   * @return a list of parameters as defined in the message.
   */
  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getClassMethodCall().getParameters());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Retrieves the {@link AccessibleObject} (typically a {@link Method}) corresponding to the
   * invoked class method. It dynamically loads the target class and uses reflection to locate the
   * method matching the provided name, parameter types, and arguments.
   *
   * @param execMessage the execution message containing class method call information.
   * @param parameterTypes the expected parameter types for the method.
   * @param args the arguments to match against the method parameters.
   * @return the accessible object (method) to be invoked.
   * @throws ReflectiveOperationException if reflection fails due to class or method loading issues.
   * @throws AmbiguousCallException if the method lookup yields ambiguous results.
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException, AmbiguousCallException {
    Class<?> clazz =
        Class.forName(
            execMessage.getClassMethodCall().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return reflectionHelper.lookupMethod(
        clazz, args.toArray(), parameterTypes, execMessage.getClassMethodCall().getName());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the supported message type for this dispatcher.
   *
   * @return {@link MessageType#EXEC_CLASS_METHOD}, indicating support for class method execution
   *     messages.
   */
  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_CLASS_METHOD;
  }
}
