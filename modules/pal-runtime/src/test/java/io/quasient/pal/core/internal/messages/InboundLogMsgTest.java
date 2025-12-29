/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.internal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.MessageFormatType;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InboundLogMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 199;
    byte[] body = "whatever".getBytes(StandardCharsets.UTF_8);

    Headers emptyHeaders = new RecordHeaders();
    InboundLogMsg msgOut = new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.DEALER);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    InboundLogMsg msgIn = InboundLogMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 199;
    Headers emptyHeaders = new RecordHeaders();
    byte[] body = "whatever".getBytes(StandardCharsets.UTF_8);

    InboundLogMsg msg1 = new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body);

    // equal
    assertThat(new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body), is(msg1));

    // different offset
    assertThat(
        new InboundLogMsg(offset + 1, MessageFormatType.BINARY, emptyHeaders, body), is(not(msg1)));

    // different format
    assertThat(
        new InboundLogMsg(offset, MessageFormatType.JSON, emptyHeaders, body), is(not(msg1)));

    // different body
    assertThat(
        new InboundLogMsg(
            offset,
            MessageFormatType.BINARY,
            emptyHeaders,
            "whatevah".getBytes(StandardCharsets.UTF_8)),
        is(not(msg1)));
  }
}
