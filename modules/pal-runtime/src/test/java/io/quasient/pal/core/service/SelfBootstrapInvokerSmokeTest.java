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
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
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
