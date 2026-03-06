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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link ReplayObjectStore} — the bidirectional mapping between WAL object refs
 * (int) and live JVM objects created during replay.
 *
 * <p>Naming convention: MethodName_StateUnderTest_ExpectedBehavior.
 */
public class ReplayObjectStoreTest {

  @Test
  public void registerAndResolve() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    Object myObject = new Object();

    // When
    store.register(42, myObject);

    // Then
    assertThat(store.resolve(42), is(sameInstance(myObject)));
  }

  @Test
  public void resolveOrNullForUnknownRef() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();

    // When / Then
    assertThat(store.resolveOrNull(99), is(nullValue()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void resolveThrowsForUnknownRef() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();

    // When
    store.resolve(99);
  }

  @Test
  public void getWalRefReverseLookup() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    Object myObject = new Object();
    store.register(42, myObject);

    // When / Then
    assertThat(store.getWalRef(myObject), is(42));
  }

  @Test
  public void multipleRegistrations() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    Object obj1 = new Object();
    Object obj2 = new Object();
    store.register(1, obj1);
    store.register(2, obj2);

    // When / Then
    assertThat(store.resolve(1), is(sameInstance(obj1)));
    assertThat(store.resolve(2), is(sameInstance(obj2)));
    assertThat(store.size(), is(2));
  }

  @Test
  public void overwriteRef() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    Object obj1 = new Object();
    Object obj2 = new Object();
    store.register(42, obj1);

    // When
    store.register(42, obj2);

    // Then
    assertThat(store.resolve(42), is(sameInstance(obj2)));
    assertThat(store.size(), is(1));
  }

  @Test
  public void identityBasedReverseLookup() {
    // Given: Two equal but non-identical objects
    ReplayObjectStore store = new ReplayObjectStore();
    String obj1 = new String("test");
    String obj2 = new String("test");
    store.register(1, obj1);

    // When / Then: identity-based, so obj2 is not found even though obj1.equals(obj2)
    assertThat(store.getWalRef(obj2), is(0));
    assertThat(store.getWalRef(obj1), is(1));
  }

  // ---- Phantom reference tracking tests (Phase 4: Side-Effect Shielding) ----

  /** Tests that a phantom-registered ref is recognized as phantom. */
  @Test
  @Ignore("Awaiting implementation in #948")
  public void registerPhantomAndCheckIsPhantom() {
    // Given: Empty store
    // When: registerPhantom(42) called
    // Then: isPhantom(42) returns true

    // TODO(#948): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that isPhantom returns false for a ref that was never registered. */
  @Test
  @Ignore("Awaiting implementation in #948")
  public void isPhantomReturnsFalseForUnknownRef() {
    // Given: Empty store
    // When: isPhantom(99) called
    // Then: Returns false

    // TODO(#948): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that live objects and phantoms can coexist without interference. */
  @Test
  @Ignore("Awaiting implementation in #948")
  public void phantomAndLiveObjectCoexist() {
    // Given: Store with register(1, obj) and registerPhantom(2)
    // When: Both refs are checked
    // Then: resolveOrNull(1) returns obj, isPhantom(1) returns false,
    //       isPhantom(2) returns true, resolveOrNull(2) returns null

    // TODO(#948): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that registering a live object overrides a previous phantom registration. */
  @Test
  @Ignore("Awaiting implementation in #948")
  public void registerOverridesPhantom() {
    // Given: Store with registerPhantom(42)
    // When: register(42, realObj) called
    // Then: isPhantom(42) returns false, resolveOrNull(42) returns realObj

    // TODO(#948): Implement test logic
    fail("Not yet implemented");
  }
}
