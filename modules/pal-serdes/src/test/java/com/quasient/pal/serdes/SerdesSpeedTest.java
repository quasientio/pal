/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class SerdesSpeedTest {

  private MessageBuilder messageBuilder;
  String className = "com.quasient.pal.apps.MyClass";
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
