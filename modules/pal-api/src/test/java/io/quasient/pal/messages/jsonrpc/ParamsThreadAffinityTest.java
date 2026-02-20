/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.jsonrpc;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link Params}.
 *
 * <p>Verifies getter/setter, builder support, and inclusion in {@code equals}, {@code hashCode},
 * and {@code toString}.
 */
public class ParamsThreadAffinityTest {

  @Test
  @Ignore("Awaiting implementation in #745")
  public void threadAffinityGetterSetter() {
    // Given: Params instance
    // When: setThreadAffinity("fx-thread") then getThreadAffinity()
    // Then: Returns "fx-thread"

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void threadAffinityInEqualsAndHashCode() {
    // Given: Two Params instances, identical except threadAffinity
    // When: equals() and hashCode() compared
    // Then: Not equal, different hashCodes

    // TODO(#745): Implement test logic
    // Build two Params with same type/method but different threadAffinity values,
    // assert they are not equal and have different hashCodes
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void threadAffinityInBuilder() {
    // Given: Params.builder().withType("Foo").withThreadAffinity("fx-thread").build()
    // When: getThreadAffinity() called
    // Then: Returns "fx-thread"

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void threadAffinityInToString() {
    // Given: Params with threadAffinity set
    // When: toString() called
    // Then: Contains "threadAffinity"

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }
}
