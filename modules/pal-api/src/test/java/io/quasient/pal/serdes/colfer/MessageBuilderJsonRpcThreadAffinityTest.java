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
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;

import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for threadAffinity propagation through {@link MessageBuilder#jsonRpcRequestToExecMessage}.
 *
 * <p>Verifies that the {@code threadAffinity} field on JSON-RPC {@code Params} is correctly copied
 * to the resulting {@code ExecMessage} when converting from JSON-RPC request format to the internal
 * Colfer message format.
 */
public class MessageBuilderJsonRpcThreadAffinityTest {

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder builder;

  @Before
  public void setUp() {
    builder = new MessageBuilder(peerId);
  }

  @Test
  public void jsonRpcRequestToExecMessagePreservesThreadAffinity() {
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildClassMethodCall("com.example.Foo", "bar", new ArrayList<>());
    req.getParams().setThreadAffinity("fx-thread");

    Message msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();

    assertThat(em.getThreadAffinity(), is("fx-thread"));
  }

  @Test
  public void jsonRpcRequestToExecMessageNullThreadAffinity() {
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildClassMethodCall("com.example.Foo", "bar", new ArrayList<>());
    // threadAffinity is null by default

    Message msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();

    assertThat(em.getThreadAffinity(), is(emptyString()));
  }

  @Test
  public void jsonRpcRequestToExecMessageEmptyThreadAffinity() {
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildClassMethodCall("com.example.Foo", "bar", new ArrayList<>());
    req.getParams().setThreadAffinity("");

    Message msg = builder.jsonRpcRequestToExecMessage(req, peerId);
    ExecMessage em = msg.getExecMessage();

    // Empty string treated as absent — threadAffinity remains at Colfer default ("")
    assertThat(em.getThreadAffinity(), is(emptyString()));
  }
}
