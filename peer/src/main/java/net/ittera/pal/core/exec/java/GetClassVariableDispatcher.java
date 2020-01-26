package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.ObjectStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;

@Singleton
public class GetClassVariableDispatcher extends GetFieldDispatcher {

  @Inject
  public GetClassVariableDispatcher(
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
  protected final ExecMessageType getBeforeExecMessageType() {
    return ExecMessageType.GET_STATIC;
  }

  @Override
  protected final ExecMessageType getAfterExecMessageType() {
    return ExecMessageType.RETURN_VALUE;
  }

  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class clazz =
        Class.forName(
            execMessage.getStaticFieldGet().getClass_().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return clazz.getDeclaredField(execMessage.getStaticFieldGet().getField().getName());
  }
}
