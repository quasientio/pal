/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  // ---- Phantom reference tracking tests (Phase 4: Side-Effect Shielding) ----

  /** Tests that a phantom-registered ref is recognized as phantom. */
  @Test
  public void registerPhantomAndCheckIsPhantom() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();

    // When
    store.registerPhantom(42);

    // Then
    assertThat(store.isPhantom(42), is(true));
  }

  /** Tests that isPhantom returns false for a ref that was never registered. */
  @Test
  public void isPhantomReturnsFalseForUnknownRef() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();

    // When / Then
    assertThat(store.isPhantom(99), is(false));
  }

  /** Tests that live objects and phantoms can coexist without interference. */
  @Test
  public void phantomAndLiveObjectCoexist() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    Object obj = new Object();
    store.register(1, obj);
    store.registerPhantom(2);

    // When / Then
    assertThat(store.resolveOrNull(1), is(sameInstance(obj)));
    assertThat(store.isPhantom(1), is(false));
    assertThat(store.isPhantom(2), is(true));
    assertThat(store.resolveOrNull(2), is(nullValue()));
  }

  /** Tests that registering a live object overrides a previous phantom registration. */
  @Test
  public void registerOverridesPhantom() {
    // Given
    ReplayObjectStore store = new ReplayObjectStore();
    store.registerPhantom(42);
    Object realObj = new Object();

    // When
    store.register(42, realObj);

    // Then
    assertThat(store.isPhantom(42), is(false));
    assertThat(store.resolveOrNull(42), is(sameInstance(realObj)));
  }
}
