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
import static org.junit.Assert.fail;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.Marshallable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InterceptEventMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendRegister() {

    final byte[] body = "actual body is a intercept message".getBytes(StandardCharsets.UTF_8);
    final var type = InterceptEventMsg.Type.REGISTER;

    InterceptEventMsg msg = new InterceptEventMsg(body);

    // verify getters
    assertThat(msg.getBody(), is(body));
    assertThat(msg.getType(), is(type));

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msg.send(out);

    // receive and compare
    InterceptEventMsg msgIn = InterceptEventMsg.receive(in, true);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendUnregister() {

    final String interceptMsgId = UUID.randomUUID().toString();
    final var type = InterceptEventMsg.Type.UNREGISTER;

    InterceptEventMsg msg = new InterceptEventMsg(interceptMsgId);

    // verify getters
    assertThat(msg.getType(), is(type));
    assertThat(msg.getInterceptMessageId(), is(interceptMsgId));

    // send
    String socketAddress = "inproc://here";
    ZContext mqzContext = createContext();
    ZMQ.Socket in = mqzContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = mqzContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msg.send(out);
    logger.debug("sent msg= {}", msg);

    // receive and compare
    InterceptEventMsg msgIn = InterceptEventMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    mqzContext.destroy();
  }

  @Test
  public void testNullPointerException() {
    try {
      new InterceptEventMsg((Marshallable) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEventMsg((byte[]) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEventMsg((String) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    // REGISTER type
    byte[] body = "actual body is not a string".getBytes(StandardCharsets.UTF_8);
    InterceptEventMsg msg = new InterceptEventMsg(body);

    // assert equality
    assertThat(new InterceptEventMsg(body), is(msg));

    // different body
    assertThat(
        new InterceptEventMsg("another body".getBytes(StandardCharsets.UTF_8)), is(not(msg)));

    // UNREGISTER type
    String interceptMsgId = UUID.randomUUID().toString();
    msg = new InterceptEventMsg(interceptMsgId);

    // assert equality
    assertThat(new InterceptEventMsg(interceptMsgId), is(msg));

    // different messageId
    assertThat(new InterceptEventMsg(UUID.randomUUID().toString()), is(not(msg)));
  }
}
