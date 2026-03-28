/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class SerdesSpeedTest {

  private MessageBuilder messageBuilder;
  String className = "io.quasient.foobar.apps.MyClass";
  Object[] args = {
    new String[] {"A normal string", "Boring to say the least", "The loong winter is comiiing"}
  };
  ObjectRef[] argRefs = {null};
  Class<?>[] parameterTypes = {String[].class};
  private String[] parameterTypesNamesArray;

  @Before
  public void init() {
    messageBuilder = new MessageBuilder();
    parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
  }

  @Test
  public void colferSerdes() {
    Instant start = Instant.now();
    int messagesToCreate = 10000;
    for (int i = 0; i < messagesToCreate; i++) {
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
        messagesToCreate, Duration.between(start, end).toMillis());
  }
}
