package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.ObjectStore;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.ObjectNotFoundException;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Primitives;

@Singleton
public class InstanceMethodDispatcher extends MethodDispatcher {

  @Inject
  public InstanceMethodDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      ObjectStore objectStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setObjectStore(objectStore);
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
  protected List<Primitives.Parameter> getParameterList(ExecMessage execMessage) {
    return execMessage.getInstanceMethodCall().getParameterList();
  }

  @Override
  protected Object getTargetFromMessage(
      ExecMessage execMessage, Optional<AccessibleObject> accessibleObject)
      throws ClassNotFoundException, ObjectNotFoundException {
    Object target;
    if (execMessage.getInstanceMethodCall().hasObject()) {
      Class objClass =
          Class.forName(
              execMessage.getInstanceMethodCall().getClass_().getName(),
              true,
              Thread.currentThread().getContextClassLoader());
      target = Unwrapper.unwrapObject(execMessage.getInstanceMethodCall().getObject(), objClass);
      if (logger.isTraceEnabled()) {
        logger.trace("Unwrapped target: {}", target);
      }
    } else {
      ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceMethodCall().getObjectRef());
      if (objectStore.containsObjectRef(targetObjRef)) {
        target = objectStore.lookupObject(targetObjRef);
      } else {
        throw new ObjectNotFoundException(
            String.format("No object found with objRef: %d", targetObjRef.getRef()));
      }
      if (logger.isTraceEnabled()) {
        logger.trace("Loaded target: {}", target);
      }
    }
    return target;
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
            execMessage.getInstanceMethodCall().getClass_().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    AccessibleObject accessibleObject =
        ReflectionHelper.getMethodToInvoke(
            clazz,
            args.toArray(),
            execMessage.getInstanceMethodCall().getParameterList().stream()
                .map(Primitives.Parameter::getValue)
                .collect(Collectors.toList()),
            execMessage.getInstanceMethodCall().getName());
    if (accessibleObject == null) {
      // TODO perhaps this should be thrown by ReflectionHelper instead
      throw new NoSuchMethodException(
          String.format(
              "Can't find method:%s in class:%s with given parameter types",
              execMessage.getInstanceMethodCall().getName(), clazz.getName()));
    }
    return accessibleObject;
  }
}
