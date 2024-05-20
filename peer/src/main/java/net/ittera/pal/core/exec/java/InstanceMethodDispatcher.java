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

package net.ittera.pal.core.exec.java;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectNotFoundException;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.core.exec.java.reflect.ReflectionHelper;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.colfer.Unwrapper;

@Singleton
public class InstanceMethodDispatcher extends MethodDispatcher {

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

  @Override
  protected final ExecMessage wrapBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    return messageBuilder.buildInstanceMethod(
        peerUuid,
        ctxt,
        sender,
        storeObject(sender),
        target,
        storeObject(target),
        args,
        Arrays.stream(args).map(this::storeObject).toArray(ObjectRef[]::new));
  }

  @Override
  protected ExecMessage wrapAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject method = ((MethodSignature) ctxt.getSignature()).getMethod();

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).exception();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, method, ExecutableObjectType.METHOD, invocationException, null);
    } else {
      return messageBuilder.buildReturnValue(peerUuid, value, method, objectRef, isVoid, null);
    }
  }

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

  @Override
  protected final ExecMessageType getBeforeExecMessageType() {
    return ExecMessageType.INSTANCE_METHOD;
  }

  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getInstanceMethodCall().getParameters());
  }

  @Override
  protected Object getTargetFromMessage(ExecMessage execMessage)
      throws ClassNotFoundException, NullPointerException {
    final Obj methodCallObject = execMessage.getInstanceMethodCall().getObject();
    if (logger.isTraceEnabled()) {
      logger.trace("methodCallObject: {}", methodCallObject);
      if (execMessage.getInstanceMethodCall().getObjectRef() != null
          && !execMessage.getInstanceMethodCall().getObjectRef().isEmpty()) {
        logger.trace("ObjectRef: {}", execMessage.getInstanceMethodCall().getObjectRef());
      }
    }

    Object target;
    if (methodCallObject != null) {
      Class<?> objClass =
          Class.forName(
              execMessage.getInstanceMethodCall().getClazz().getName(),
              true,
              Thread.currentThread().getContextClassLoader());
      target = Unwrapper.unwrapObject(methodCallObject, objClass);
      if (logger.isTraceEnabled()) {
        logger.trace("Unwrapped target: {}", target);
      }
    } else {
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
    }
    return target;
  }

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
}
