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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectNotFoundException;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.core.rpc.DispatcherConnector;
import net.ittera.pal.core.rpc.exec.java.reflect.ReflectionHelper;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;

/**
 * InstanceMethodDispatcher dispatches and executes instance method calls via reflection within the
 * PAL runtime.
 *
 * <p>It is responsible for constructing execution messages before and after the method invocation,
 * invoking the target method on instance objects, and handling any exceptions that occur during the
 * reflective call. The dispatcher integrates with the object lookup store and reflection helper to
 * resolve and execute instance methods.
 */
@Singleton
public class InstanceMethodDispatcher extends MethodDispatcher {

  /**
   * Constructs a new InstanceMethodDispatcher configured to dispatch instance method calls.
   *
   * @param peerUuid a unique identifier representing the peer.
   * @param messageBuilder a builder for constructing execution messages.
   * @param connector the connector used for message routing.
   * @param allowNonPublicAccess string flag controlling non-public method access (e.g., "true" or
   *     "false").
   * @param reflectionHelper helper for reflective operations to resolve target methods.
   * @param objectLookupStore store for object references used during target object lookup.
   */
  @Inject
  public InstanceMethodDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      @Named("rpc.allow_nonpublic") String allowNonPublicAccess,
      ReflectionHelper reflectionHelper,
      ObjectLookupStore objectLookupStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
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
    return messageBuilder.buildInstanceMethod(
        peerUuid,
        ctxt,
        sender,
        storeObject(sender),
        storeObject(target),
        args,
        Arrays.stream(args).map(this::storeObject).toArray(ObjectRef[]::new));
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
   * @param value the result of the method invocation, or an InvocationExceptionWrapper in case of
   *     error.
   * @param objectRef the object reference associated with the target, used for object management.
   * @param isVoid flag indicating whether the method's return type is void.
   * @return an ExecMessage representing either the method's return value or an invocation error.
   */
  @Override
  protected ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject method = ((MethodSignature) ctxt.getSignature()).getMethod();

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).exception();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, method, invocationException, null);
    } else {
      return messageBuilder.buildReturnValue(peerUuid, value, method, objectRef, isVoid, null);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Invokes the target instance method on the provided object using reflection. The method
   * ensures accessibility and handles exceptions by wrapping them into an
   * InvocationExceptionWrapper. For methods with a void return type, a standard void instance is
   * returned.
   *
   * @param ctxt the execution context containing method signature details.
   * @param sender the calling object, provided for context and logging.
   * @param target the target object on which the method is to be executed.
   * @param args the arguments to be passed to the target method.
   * @return the result of the method invocation, or an InvocationExceptionWrapper if an error
   *     occurred.
   */
  @Override
  protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invoke w/ ctxt: {}, sender: {}, target: {}, args: {}",
          ctxt,
          sender,
          target,
          Arrays.toString(args));
    }
    final MethodSignature methodSignature = (MethodSignature) ctxt.getSignature();
    Method method = methodSignature.getMethod();

    method.setAccessible(true);
    Object returnValue;
    try {
      returnValue = method.invoke(target, args);
    } catch (Exception ex) {
      logger.error("Caught exception while invoking instance method. Will wrap and return it.", ex);
      return new InvocationExceptionWrapper(ex);
    }

    if (method.getReturnType().equals(java.lang.Void.TYPE)) {
      return Void.getInstance();
    } else {
      return returnValue;
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
