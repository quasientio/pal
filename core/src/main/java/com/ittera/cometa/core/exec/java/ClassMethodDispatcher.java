package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.common.lang.reflect.MethodSignature;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Primitives;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessageType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClassMethodDispatcher extends MethodDispatcher {

  @Inject
  public ClassMethodDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      ObjectService objectService) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setObjectService(objectService);
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

    final Optional<AccessibleObject> method =
        Optional.of(((MethodSignature) ctxt.getSignature()).getMethod());

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).getException();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, method, ExecutableObjectType.METHOD, invocationException, null);
    } else {
      return messageBuilder.buildReturnValue(
          peerUuid, value, method.get(), objectRef, isVoid, null);
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
  protected List<Primitives.Parameter> getParameterList(ExecMessage execMessage) {
    return execMessage.getClassMethodCall().getParameterList();
  }

  /**
   * @param execMessage
   * @param parameterTypes Not used here.
   * @param args
   * @return
   * @throws ReflectiveOperationException
   */
  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class clazz =
        Class.forName(
            execMessage.getClassMethodCall().getClass_().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    AccessibleObject accessibleObject =
        ReflectionHelper.getMethodToInvoke(
            clazz,
            args.toArray(),
            execMessage.getClassMethodCall().getParameterList().stream()
                .map(Primitives.Parameter::getValue)
                .collect(Collectors.toList()),
            execMessage.getClassMethodCall().getName());
    if (accessibleObject == null) {
      throw new NoSuchMethodException(
          String.format(
              "Can't find method:%s in class:%s with given parameter types",
              execMessage.getClassMethodCall().getName(), clazz.getName()));
    }
    return accessibleObject;
  }
}
