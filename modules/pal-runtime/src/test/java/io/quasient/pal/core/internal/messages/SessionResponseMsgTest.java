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

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.SessionStatusType;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SessionResponseMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendAndReceiveResponseMsgNoObjects() {
    SessionResponseMsg msgOut = new SessionResponseMsg(SessionStatusType.OK);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);

    // receive and compare
    SessionResponseMsg msgIn = SessionResponseMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendAndReceiveResponseMsgWithObjects() {

    Set<ObjectRef> objectRefs = new HashSet<>();
    objectRefs.add(ObjectRef.from("498324"));
    objectRefs.add(ObjectRef.from("2348632"));
    SessionResponseMsg msgOut = new SessionResponseMsg(SessionStatusType.OK, objectRefs);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    SessionResponseMsg msgIn = SessionResponseMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }
}
