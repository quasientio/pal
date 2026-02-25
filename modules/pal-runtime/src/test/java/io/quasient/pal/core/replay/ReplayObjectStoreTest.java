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
 * Unit tests for {@code ReplayObjectStore} — the bidirectional mapping between WAL object refs
 * (int) and live JVM objects created during replay.
 *
 * <p>Naming convention: MethodName_StateUnderTest_ExpectedBehavior.
 */
public class ReplayObjectStoreTest {

  @Test
  @Ignore("Awaiting implementation in #809")
  public void registerAndResolve() {
    // Given: register(42, myObject)
    // When: resolve(42)
    // Then: returns myObject

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void resolveOrNullForUnknownRef() {
    // Given: empty store
    // When: resolveOrNull(99)
    // Then: returns null

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void resolveThrowsForUnknownRef() {
    // Given: empty store
    // When: resolve(99)
    // Then: throws IllegalArgumentException or similar

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void getWalRefReverseLookup() {
    // Given: register(42, myObject)
    // When: getWalRef(myObject)
    // Then: returns 42

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void multipleRegistrations() {
    // Given: register(1, obj1), register(2, obj2)
    // When: resolve(1), resolve(2)
    // Then: returns obj1, obj2 respectively

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void overwriteRef() {
    // Given: register(42, obj1), then register(42, obj2)
    // When: resolve(42)
    // Then: returns obj2 (latest)

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #809")
  public void identityBasedReverseLookup() {
    // Given: Two equal but non-identical objects. register(1, obj1).
    // When: getWalRef(obj2)
    // Then: returns 0 or throws (identity-based, not equals-based)

    // TODO(#809): Implement test logic
    fail("Not yet implemented");
  }
}
