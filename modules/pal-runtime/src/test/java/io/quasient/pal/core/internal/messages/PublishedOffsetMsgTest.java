/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.internal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quasient.pal.core.ZmqEnabledTest;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class PublishedOffsetMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 472;
    String messageId = UUID.randomUUID().toString();

    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageId);

    // verify getters
    assertThat(msgOut.getOffset(), is(offset));
    assertThat(msgOut.getMessageId(), is(messageId));

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 472;
    String messageId = UUID.randomUUID().toString();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageId);

    // assert content equality
    assertThat(new PublishedOffsetMsg(offset, messageId), is(msg1));

    // different offset
    assertThat(new PublishedOffsetMsg(offset + 1, messageId), is(not(msg1)));

    // different messageId
    assertThat(new PublishedOffsetMsg(offset, UUID.randomUUID().toString()), is(not(msg1)));
  }
}
