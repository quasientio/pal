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
}
