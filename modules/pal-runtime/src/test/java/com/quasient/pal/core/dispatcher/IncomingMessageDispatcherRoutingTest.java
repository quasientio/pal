/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quasient.pal.core.execution.java.ClassMethodDispatcher;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class IncomingMessageDispatcherRoutingTest {

  private IncomingMessageDispatcher dispatcher;
  private final UUID peer = UUID.randomUUID();
  private final MessageBuilder builder = new MessageBuilder(peer);

  private static void set(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Before
  public void setup() throws Exception {
    dispatcher = new IncomingMessageDispatcher();
    // Inject only the classMethodDispatcher mock; other paths unused in this test
    ClassMethodDispatcher cmd = mock(ClassMethodDispatcher.class);
    when(cmd.dispatchIncoming(any(), any())).thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    set(dispatcher, "classMethodDispatcher", cmd);
  }

  @Test
  public void routesClassMethodMessage_toClassMethodDispatcher() {
    ExecMessage req =
        builder.buildClassMethod(
            peer,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_CLASS_METHOD, MessageChannelType.CLI_RPC);
    // Our stub echoes the same ExecMessage
    assertThat(resp, is(req));
  }
}
