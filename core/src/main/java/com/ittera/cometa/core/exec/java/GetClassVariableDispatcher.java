package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetClassVariableDispatcher extends GetFieldDispatcher {

  @Inject
  public GetClassVariableDispatcher(
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
  protected final Type getBeforeExecMessageType() {
    return Type.GET_STATIC;
  }

  @Override
  protected final Type getAfterExecMessageType() {
    return Type.RETURN_VALUE;
  }

  @Override
  protected AccessibleObject loadAccessibleObject(
      Wrappers.ExecMessage execMessage, List<Class> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class clazz =
        Class.forName(
            execMessage.getStaticFieldGet().getClass_().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return clazz.getDeclaredField(execMessage.getStaticFieldGet().getField().getName());
  }
}
