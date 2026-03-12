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

  // ===== Tests for priority-based ordering in cloneListWithNewRequest() =====

  /**
   * Builds a method intercept message targeting "com.example.Foo.bar()" with the given priority.
   */
  private InterceptMessage buildMethodInterceptWithPriority(
      MessageBuilder msgBuilder, int priority) {
    InterceptMessage msg =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Foo",
            "bar",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    msg.setPriority(priority);
    return msg;
  }

  /** Matches all method intercepts for "com.example.Foo.bar()" and returns the result list. */
  private List<InterceptMessage> matchFooBar(InterceptRequests interceptRequests) {
    return interceptRequests.getMatchingIntercepts(
        "com.example.Foo", "bar", new String[0], MessageType.EXEC_INSTANCE_METHOD);
  }

  /** All-default-priority preserves insertion order. */
  @Test
  public void testDefaultPriorityPreservesRegistrationOrder() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 0);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, 0);
    InterceptMessage c = buildMethodInterceptWithPriority(msgBuilder, 0);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);
    interceptRequests.registerInterceptRequest(c);

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(3));
    assertThat(matches.get(0).getMessageId(), is(a.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(b.getMessageId()));
    assertThat(matches.get(2).getMessageId(), is(c.getMessageId()));
  }

  /** Explicit priorities sort ascending. */
  @Test
  public void testExplicitPrioritySortsCorrectly() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 10);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, 1);
    InterceptMessage c = buildMethodInterceptWithPriority(msgBuilder, 5);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);
    interceptRequests.registerInterceptRequest(c);

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(3));
    assertThat(matches.get(0).getMessageId(), is(b.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(c.getMessageId()));
    assertThat(matches.get(2).getMessageId(), is(a.getMessageId()));
  }

  /** Same priority preserves insertion order (stable sort). */
  @Test
  public void testSamePriorityPreservesRegistrationOrder() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 1);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, 1);
    InterceptMessage c = buildMethodInterceptWithPriority(msgBuilder, 1);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);
    interceptRequests.registerInterceptRequest(c);

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(3));
    assertThat(matches.get(0).getMessageId(), is(a.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(b.getMessageId()));
    assertThat(matches.get(2).getMessageId(), is(c.getMessageId()));
  }

  /** Negative priority sorts before zero. */
  @Test
  public void testNegativePriorityExecutesFirst() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 0);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, -1);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(2));
    assertThat(matches.get(0).getMessageId(), is(b.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(a.getMessageId()));
  }

  /** Mixed priorities sort correctly with stable tie-breaking. */
  @Test
  public void testMixedPriorityAndDefault() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 0);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, -5);
    InterceptMessage c = buildMethodInterceptWithPriority(msgBuilder, 3);
    InterceptMessage d = buildMethodInterceptWithPriority(msgBuilder, 0);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);
    interceptRequests.registerInterceptRequest(c);
    interceptRequests.registerInterceptRequest(d);

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(4));
    assertThat(matches.get(0).getMessageId(), is(b.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(a.getMessageId()));
    assertThat(matches.get(2).getMessageId(), is(d.getMessageId()));
    assertThat(matches.get(3).getMessageId(), is(c.getMessageId()));
  }

  /** Unregister preserves sorted order of remaining entries. */
  @Test
  public void testUnregisterPreservesPriorityOrder() throws Exception {
    MessageBuilder msgBuilder = new MessageBuilder();
    InterceptMessage a = buildMethodInterceptWithPriority(msgBuilder, 2);
    InterceptMessage b = buildMethodInterceptWithPriority(msgBuilder, 1);
    InterceptMessage c = buildMethodInterceptWithPriority(msgBuilder, 3);

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(a);
    interceptRequests.registerInterceptRequest(b);
    interceptRequests.registerInterceptRequest(c);

    interceptRequests.unregisterInterceptRequest(b.getMessageId());

    List<InterceptMessage> matches = matchFooBar(interceptRequests);
    assertThat(matches.size(), is(2));
    assertThat(matches.get(0).getMessageId(), is(a.getMessageId()));
    assertThat(matches.get(1).getMessageId(), is(c.getMessageId()));
  }
}
