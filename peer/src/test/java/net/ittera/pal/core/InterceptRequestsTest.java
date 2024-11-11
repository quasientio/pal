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

package net.ittera.pal.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.core.exec.DuplicateInterceptException;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.Test;

public class InterceptRequestsTest {

  @Test
  public void getMatchingIntercepts() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    // register request
    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now try matching ExecMessage
    ExecMessage execMessage =
        msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.util.ArrayList");

    List<InterceptMessage> matchedIntercepts = interceptRequests.getMatchingIntercepts(execMessage);
    assertThat(matchedIntercepts, is(notNullValue()));
    assertThat(matchedIntercepts.size(), is(1));
  }

  @Test
  public void registerInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptRequests interceptRequests = new InterceptRequests();
    // pre-assertion
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(0));

    // register and verify
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));
  }

  @Test
  public void registerDupInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now try to re-register
    try {
      interceptRequests.registerInterceptRequest(interceptMessage);
      fail("Should have raised DuplicateInterceptException");
    } catch (DuplicateInterceptException e) {
      // ok
      assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));
    }
  }

  @Test
  public void unregisterInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    // register request
    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now unregister
    interceptRequests.unregisterInterceptRequest(interceptMessage.getMessageUuid());
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(0));
  }
}
