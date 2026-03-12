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
import org.junit.Ignore;
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
   * [TEST:InterceptRequestsTest.testDefaultPriorityPreservesRegistrationOrder] All-default-priority
   * preserves insertion order.
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testDefaultPriorityPreservesRegistrationOrder() {
    // Given: Three InterceptMessages A, B, C all with priority=0 (default)
    // When: Registered in order A, B, C; then match
    // Then: Matches returned in order A, B, C (stable sort preserves insertion order)

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * [TEST:InterceptRequestsTest.testExplicitPrioritySortsCorrectly] Explicit priorities sort
   * ascending.
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testExplicitPrioritySortsCorrectly() {
    // Given: Three InterceptMessages A(p=10), B(p=1), C(p=5)
    // When: Registered in order A, B, C; then match
    // Then: Matches returned in order B, C, A (ascending priority)

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * [TEST:InterceptRequestsTest.testSamePriorityPreservesRegistrationOrder] Same priority preserves
   * insertion order (stable sort).
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testSamePriorityPreservesRegistrationOrder() {
    // Given: Three InterceptMessages A(p=1), B(p=1), C(p=1)
    // When: Registered in order A, B, C; then match
    // Then: Matches returned in order A, B, C (stable sort, same priority = insertion order)

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * [TEST:InterceptRequestsTest.testNegativePriorityExecutesFirst] Negative priority sorts before
   * zero.
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testNegativePriorityExecutesFirst() {
    // Given: Two InterceptMessages A(p=0), B(p=-1)
    // When: Registered in order A, B; then match
    // Then: Matches returned in order B, A (lower priority first)

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * [TEST:InterceptRequestsTest.testMixedPriorityAndDefault] Mixed priorities sort correctly with
   * stable tie-breaking.
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testMixedPriorityAndDefault() {
    // Given: Four InterceptMessages A(p=0), B(p=-5), C(p=3), D(p=0)
    // When: Registered in order A, B, C, D; then match
    // Then: Matches returned in order B, A, D, C

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * [TEST:InterceptRequestsTest.testUnregisterPreservesPriorityOrder] Unregister preserves sorted
   * order of remaining entries.
   */
  @Test
  @Ignore("Awaiting implementation in #1069")
  public void testUnregisterPreservesPriorityOrder() {
    // Given: Three InterceptMessages A(p=2), B(p=1), C(p=3) registered (sorted to B, A, C)
    // When: Unregister B; then match
    // Then: Matches returned in order A, C (priority order preserved)

    // TODO(#1069): Implement test logic
    fail("Not yet implemented");
  }
}
