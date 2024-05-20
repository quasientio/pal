/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashSet;
import java.util.Set;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.messages.types.SessionStatusType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SessionReplyMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendAndReceiveReplyMsgNoObjects() {
    SessionReplyMsg msgOut = new SessionReplyMsg(SessionStatusType.OK);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);

    // receive and compare
    SessionReplyMsg msgIn = SessionReplyMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendAndReceiveReplyMsgWithObjects() {

    Set<ObjectRef> objectRefs = new HashSet<>();
    objectRefs.add(ObjectRef.from("498324"));
    objectRefs.add(ObjectRef.from("2348632"));
    SessionReplyMsg msgOut = new SessionReplyMsg(SessionStatusType.OK, objectRefs);

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
    SessionReplyMsg msgIn = SessionReplyMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }
}
