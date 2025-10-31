/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.net.URL;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class SelfBootstrapInvokerSmokeTest {
  private ZContext ctx;

  @Before
  public void setup() {
    ctx = new ZContext(1);
  }

  @After
  public void cleanup() {
    ctx.close();
  }

  @Test
  public void callMain_returnsExitCodeFromResponse() throws Exception {
    UUID peer = UUID.randomUUID();
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    MessageBuilder mb = new MessageBuilder(peer);
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());

    // dispatcher returns an ExecMessage with integer return value 7
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method m = Integer.class.getMethod("intValue");
              return mb.buildReturnValue(Integer.valueOf(7), m, null, false, req.getMessageId());
            });

    SelfBootstrapInvoker invoker =
        new SelfBootstrapInvoker(
            peer, dispatcher, mb, cl, ctx, "inproc://offs", Collections.emptySet());

    int rc = invoker.callMain("java.lang.String", Collections.emptyList());
    assertThat(rc, is(7));
  }
}
