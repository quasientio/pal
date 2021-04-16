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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.UUID;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.messages.InterceptEvtMsg.Type;
import net.ittera.pal.messages.Marshallable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InterceptEvtMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendRegister() {

    final byte[] body = "actual body is a intercept message".getBytes();
    final Type type = Type.REGISTER;

    InterceptEvtMsg msg = new InterceptEvtMsg(body);

    // verify getters
    assertThat(msg.getBody(), is(body));
    assertThat(msg.getType(), is(type));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msg.send(out);
    logger.debug("sent msg= {}", msg);

    // receive and compare
    InterceptEvtMsg msgIn = InterceptEvtMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void sendUnregister() {

    final UUID interceptMsgUuid = UUID.randomUUID();
    final Type type = Type.UNREGISTER;

    InterceptEvtMsg msg = new InterceptEvtMsg(interceptMsgUuid);

    // verify getters
    assertThat(msg.getType(), is(type));
    assertThat(msg.getInterceptMsgUUID(), is(interceptMsgUuid));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msg.send(out);
    logger.debug("sent msg= {}", msg);

    // receive and compare
    InterceptEvtMsg msgIn = InterceptEvtMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void testNPE() {
    try {
      new InterceptEvtMsg((Marshallable) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEvtMsg((byte[]) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEvtMsg((UUID) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    // REGISTER type
    byte[] body = "actual body is not a string".getBytes();
    InterceptEvtMsg msg = new InterceptEvtMsg(body);

    // assert equality
    assertThat(new InterceptEvtMsg(body), is(msg));

    // different body
    assertThat(new InterceptEvtMsg("another body".getBytes()), is(not(msg)));

    // UNREGISTER type
    UUID interceptMsgUuid = UUID.randomUUID();
    msg = new InterceptEvtMsg(interceptMsgUuid);

    // assert equality
    assertThat(new InterceptEvtMsg(interceptMsgUuid), is(msg));

    // different messageUuid
    assertThat(new InterceptEvtMsg(UUID.randomUUID()), is(not(msg)));
  }
}
