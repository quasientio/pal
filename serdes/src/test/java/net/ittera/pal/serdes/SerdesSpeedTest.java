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

package net.ittera.pal.serdes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class SerdesSpeedTest {

  private MessageBuilder messageBuilder;
  private final int MESSAGES_TO_CREATE = 1000000;
  String className = "net.ittera.pal.apps.MyClass";
  Object[] args = {
    new String[] {"A normal string", "Boring to say the least", "The loong winter is comiiing"}
  };
  ObjectRef[] argRefs = {null};
  Class[] parameterTypes = {String[].class};
  private String[] parameterTypesNamesArray;

  @Before
  public void init() {
    messageBuilder = new MessageBuilder();
    parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
  }

  @Ignore
  @Test
  public void colferSerdes() {
    Instant start = Instant.now();
    for (int i = 0; i < MESSAGES_TO_CREATE; i++) {
      ExecMessage msg =
          messageBuilder.buildNonEmptyConstructor(
              UUID.randomUUID(), className, parameterTypesNamesArray, args, argRefs);

      byte[] msgBytes = ColferUtils.toBytes(msg);
      ExecMessage deserialized = new ExecMessage();
      deserialized.unmarshal(msgBytes, 0);
    }
    Instant end = Instant.now();
    System.out.printf(
        "%d messages serialized+deserialized with colfer in %d ms%n",
        MESSAGES_TO_CREATE, Duration.between(start, end).toMillis());
  }
}
