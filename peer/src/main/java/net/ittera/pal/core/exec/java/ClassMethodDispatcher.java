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
import net.ittera.pal.common.lang.reflect.MethodSignature;
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
public class ClassMethodDispatcher extends MethodDispatcher {

  @Inject
  public ClassMethodDispatcher(
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
    return messageBuilder.buildClassMethod(
        peerUuid,
        ctxt,
        sender,
        storeObject(sender),
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
          peerUuid, method, invocationException, null);
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

    Object returnValue;
    method.setAccessible(true);
    try {
      returnValue = method.invoke(null, args);
    } catch (Exception ex) {
      logger.error("Caught exception while invoking class method. Will wrap and return it.", ex);
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
    return ExecMessageType.CLASS_METHOD;
  }

  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return Arrays.asList(execMessage.getClassMethodCall().getParameters());
  }

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
}
