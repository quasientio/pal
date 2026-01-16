/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
            "new",
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

    // Extract matching info from ExecMessage
    String className = ExecMessageUtils.getClassname(execMessage);
    String executableName = ExecMessageUtils.getExecutableName(execMessage);
    String[] parameterTypes =
        ExecMessageUtils.getParameterTypes(execMessage).toArray(new String[0]);

    List<InterceptMessage> matchedIntercepts =
        interceptRequests.getMatchingIntercepts(
            className, executableName, parameterTypes, MessageType.EXEC_CONSTRUCTOR);
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
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    // register request
    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now unregister
    interceptRequests.unregisterInterceptRequest(interceptMessage.getMessageId());
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(0));
  }
}
