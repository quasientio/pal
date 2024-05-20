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

import static java.lang.Class.forName;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.core.exec.java.reflect.ReflectionHelper;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;

@Singleton
public class ConstructorDispatcher extends BaseExecMessageDispatcher {

  @Inject
  public ConstructorDispatcher(
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

    return messageBuilder.buildConstructor(
        peerUuid,
        ctxt,
        sender,
        storeObject(sender),
        args,
        Arrays.stream(args).map(this::storeObject).toArray(ObjectRef[]::new));
  }

  @Override
  protected final ExecMessage wrapAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    final AccessibleObject constructor =
        ((ConstructorSignature) ctxt.getSignature()).getConstructor();

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).exception();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, constructor, getExecutableObjectType(), invocationException, null);
    } else {
      return messageBuilder.buildReturnValue(peerUuid, value, constructor, objectRef, false, null);
    }
  }

  @Override
  protected ExecMessage wrapAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    String messageUuid = execMessage.getMessageUuid();

    if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
      return wrapAfterExecThrowableMessage(
          messageUuid,
          accessibleObject,
          getExecutableObjectType(),
          exceptionWhileLoading,
          exceptionWhileInvoking);
    }

    return messageBuilder.buildReturnValue(
        peerUuid, valueObject, accessibleObject, valueObjRef, false, messageUuid);
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
    final Constructor<?> constructor =
        ((ConstructorSignature) ctxt.getSignature()).getConstructor();
    Object newObject;
    constructor.setAccessible(true);
    try {
      newObject = constructor.newInstance(args);
    } catch (Exception ex) {
      logger.error("Caught exception while invoking constructor. Will wrap and return it.", ex);
      return new InvocationExceptionWrapper(ex);
    }

    return newObject;
  }

  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<Object> args, Object value)
      throws Exception {
    // discard target and value
    return invokeIncoming(accessibleObject, args);
  }

  private Object invokeIncoming(AccessibleObject accessibleObject, List<Object> args)
      throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace("invokeIncoming:in w/ accessibleObject: {}, args: {}", accessibleObject, args);
    }
    Constructor<?> constructor = (Constructor<?>) accessibleObject;
    return constructor.newInstance(args.toArray(new Object[0]));
  }

  @Override
  protected final boolean returnsVoid(AccessibleObject accessibleObject) {
    return false;
  }

  @Override
  protected final ExecMessageType getBeforeExecMessageType() {
    return ExecMessageType.CONSTRUCTOR;
  }

  @Override
  protected final ExecutableObjectType getExecutableObjectType() {
    return ExecutableObjectType.CONSTRUCTOR;
  }

  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getConstructorCall().getParameters());
  }

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
}
