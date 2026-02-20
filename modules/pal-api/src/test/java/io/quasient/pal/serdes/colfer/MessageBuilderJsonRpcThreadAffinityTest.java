/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
