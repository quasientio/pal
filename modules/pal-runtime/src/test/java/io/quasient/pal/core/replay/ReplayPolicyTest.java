/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayPolicy} — the action resolution component that determines what replay
 * action to take for each operation. In Phase 1, the policy is hardcoded to always return {@code
 * RE_EXECUTE}.
 *
 * <p>Tests verify that RE_EXECUTE is returned for all operation types including instance methods,
 * constructors, and static methods.
 */
public class ReplayPolicyTest {

  /** Verifies that the default policy always returns RE_EXECUTE regardless of input. */
  @Test
  @Ignore("Awaiting implementation in #807")
  public void alwaysReturnsReExecute() {
    // Given: A default ReplayPolicy instance
    // When: getAction() is called with any className, methodName, and MessageType
    // Then: Returns RE_EXECUTE

    // TODO(#807): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that RE_EXECUTE is returned for constructor operations. */
  @Test
  @Ignore("Awaiting implementation in #807")
  public void reExecuteForConstructor() {
    // Given: A default ReplayPolicy instance
    // When: getAction("Foo", "new", EXEC_CONSTRUCTOR) is called
    // Then: Returns RE_EXECUTE

    // TODO(#807): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that RE_EXECUTE is returned for static method operations. */
  @Test
  @Ignore("Awaiting implementation in #807")
  public void reExecuteForStaticMethod() {
    // Given: A default ReplayPolicy instance
    // When: getAction("Foo", "bar", EXEC_CLASS_METHOD) is called
    // Then: Returns RE_EXECUTE

    // TODO(#807): Implement test logic
    fail("Not yet implemented");
  }
}
