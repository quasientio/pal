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

import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.ExecMessageType;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.MessageBuilder;

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
            execMessage.getStaticFieldGet().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return clazz.getDeclaredField(execMessage.getStaticFieldGet().getField().getName());
  }
}
