/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.messages.types.RpcType;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;

public class ThinPeerGuardsAndBuildersTest {

  @Test
  public void builderMethods_setFields_andGuardedOpsThrowWhenNotInitialized() {
    ThinPeer p =
        new ThinPeer()
            .withUuid(UUID.randomUUID())
            .withName("peer-1")
            .withZmqRpcAddress("inproc://peer")
            .withBootstrapServers("kafka:9092")
            .withLog(new LogInfo("topic-x"))
            .withLogPrefix("app")
            .withConsumerProperties(new Properties())
            .withProducerProperties(new Properties())
            .withPollingDuration(5)
            .withWebsocketConnectionLostTimeout(5)
            .withOutboundRpcType(RpcType.JSON_RPC);

    // Verify getters reflect builder-set values (no init performed)
    assertThat(p.getName(), is("peer-1"));
    assertThat(p.getBootstrapServers(), is("kafka:9092"));
    assertThat(p.getInputLog().getName(), is("topic-x"));
    assertThat(p.getOutputLog().getName(), is("topic-x"));
    assertThat(p.getLogPrefix(), is("app"));
    assertThat(p.getZmqRpcAddress(), is("inproc://peer"));
    assertThat(p.getOutboundRpcType(), is(RpcType.JSON_RPC));

    assertThrows(IllegalStateException.class, () -> p.sendJsonRpcRequestToPeer("{}", "1"));
    assertThrows(IllegalStateException.class, () -> p.sendPing(Duration.ofMillis(50)));
    assertThrows(IllegalStateException.class, () -> p.getMessageAtOffset(1L));
    assertThrows(IllegalStateException.class, p::close);
  }
}
