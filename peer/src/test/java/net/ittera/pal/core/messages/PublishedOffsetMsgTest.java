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

import java.util.UUID;
import net.ittera.pal.core.ZmqEnabledTest;
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
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageUuid);

    // verify getters
    assertThat(msgOut.getOffset(), is(offset));
    assertThat(msgOut.getMessageUuid(), is(messageUuid));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageUuid);

    // assert content equality
    assertThat(new PublishedOffsetMsg(offset, messageUuid), is(msg1));

    // different offset
    assertThat(new PublishedOffsetMsg(offset + 1, messageUuid), is(not(msg1)));

    // different messageUuid
    assertThat(new PublishedOffsetMsg(offset, UUID.randomUUID()), is(not(msg1)));
  }
}
