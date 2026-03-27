/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class IdentifiableObjectTest {

  /** Counter for generating unique ObjectRefs, decoupled from identity hash. */
  private static final AtomicInteger REF_COUNTER = new AtomicInteger(1);

  /* ------------------------------------------------------------------ */
  /*  helper: create wrapper using 3-arg constructor (identity hash)    */
  /* ------------------------------------------------------------------ */
  private IdentifiableObject wrap(Object o) {
    return new IdentifiableObject(
        o, ObjectRef.from(REF_COUNTER.getAndIncrement()), new ReferenceQueue<>());
  }

  /** Creates a wrapper with an explicit hash value (4-arg constructor). */
  private IdentifiableObject wrapWithHash(Object o, int hash) {
    return new IdentifiableObject(
        o, ObjectRef.from(REF_COUNTER.getAndIncrement()), new ReferenceQueue<>(), hash);
  }

  // ========================== constructor tests ==========================

  @Test
  public void testConstructor() {
    wrap("one, two, three");
    wrap(Arrays.asList("one", "two", "three"));

    try {
      new IdentifiableObject(null, null, new ReferenceQueue<>());
      fail("Should have raised NPE for null referent.");
    } catch (NullPointerException ignored) {
      // expected
    }
  }

  @Test(expected = NullPointerException.class)
  public void constructor_nullKey_throwsNPE() {
    new IdentifiableObject("referent", null, new ReferenceQueue<>());
  }

  @Test(expected = NullPointerException.class)
  public void constructor_nullQueue_throwsNPE() {
    new IdentifiableObject("referent", ObjectRef.from(1), null);
  }

  @Test
  public void constructor_fourArg_usesProvidedHash() {
    Object obj = new Object();
    int customHash = 42;
    IdentifiableObject wrapper = wrapWithHash(obj, customHash);
    assertThat(wrapper.getHash(), is(customHash));
    assertThat(wrapper.hashCode(), is(customHash));
  }

  @Test
  public void constructor_threeArg_usesIdentityHashCode() {
    Object obj = new Object();
    IdentifiableObject wrapper = wrap(obj);
    assertThat(wrapper.getHash(), is(System.identityHashCode(obj)));
  }

  // ========================== getHash tests ==========================

  @Test
  public void getHash_returnsStoredHash() {
    List<Float> floats = Arrays.asList(10f, 11f, 12f);
    int identityHash = System.identityHashCode(floats);
    assertThat(wrap(floats).getHash(), is(identityHash));
  }

  // ========================== hashCode tests ==========================

  @Test
  public void testHashCode() {
    List<Float> floats = Arrays.asList(10f, 11f, 12f);
    int identityHash = System.identityHashCode(floats);
    assertThat(wrap(floats).hashCode(), is(identityHash));
  }

  @Test
  public void hashCode_fourArgConstructor_matchesProvidedHash() {
    assertThat(wrapWithHash(new Object(), 999).hashCode(), is(999));
  }

  // ========================== equals tests ==========================

  @Test
  public void testEquals() {
    // same interned Integer – same identity → equal
    Integer int1 = 23;
    Integer int2 = 23;
    assertThat(wrap(int1), is(wrap(int2)));

    // different instances with same value but different identity → NOT equal
    String str1 = "ABC";
    String str2 = new String("ABC");
    assertThat(wrap(str1), is(not(wrap(str2))));

    List<Float> floats1 = Arrays.asList(10f, 11f, 12f);
    List<Float> floats2 = Arrays.asList(10f, 11f, 12f);
    assertThat(wrap(floats1), is(not(wrap(floats2))));

    // same object wrapped twice → equal
    assertThat(wrap(23), is(wrap(23)));
    assertThat(wrap("ABC"), is(wrap("ABC")));
    assertThat(wrap(floats1), is(wrap(floats1)));
    assertThat(wrap(Void.class), is(wrap(Void.class)));

    // equals with non-IdentifiableObject → false
    assertThat(wrap(int2), is(not((Object) int2)));
  }

  @Test
  public void equals_differentObjectsSameHash_notEqual() {
    Object a = new Object();
    Object b = new Object();
    int forcedHash = 42;
    IdentifiableObject wa = wrapWithHash(a, forcedHash);
    IdentifiableObject wb = wrapWithHash(b, forcedHash);

    // same hashCode but different referents → NOT equal
    assertThat(wa.hashCode(), is(wb.hashCode()));
    assertThat(wa, is(not(wb)));
  }

  @Test
  public void equals_sameReferent_differentWrappers_equal() {
    Object obj = new Object();
    IdentifiableObject w1 = wrap(obj);
    IdentifiableObject w2 = wrap(obj);

    assertThat(w1, is(w2));
    assertThat(w2, is(w1));
  }

  @Test
  public void equals_clearedReferent_notEqualToLive() {
    Object obj = new Object();
    IdentifiableObject live = wrap(obj);
    IdentifiableObject cleared = wrap(obj);
    cleared.clear(); // simulate GC

    assertThat(live, is(not(cleared)));
    assertThat(cleared, is(not(live)));
  }

  @Test
  public void equals_bothCleared_notEqual() {
    Object a = new Object();
    Object b = new Object();
    IdentifiableObject wa = wrap(a);
    IdentifiableObject wb = wrap(b);
    wa.clear();
    wb.clear();

    assertThat(wa, is(not(wb)));
  }

  @Test
  public void equals_reflexive_afterClear() {
    Object obj = new Object();
    IdentifiableObject wrapper = wrap(obj);
    wrapper.clear();

    // reflexivity must hold even after GC clears the referent
    assertThat(wrapper, is(wrapper));
  }

  // ========================== equals contract ==========================

  @Test
  public void equalsContractManually() {
    // All wrap the same interned string literal — same identity
    IdentifiableObject a1 = wrap("foo");
    IdentifiableObject a2 = wrap("foo");
    IdentifiableObject b = wrap("bar");

    // Reflexive
    assertThat(a1, is(a1));

    // Symmetric
    assertThat(a1, is(a2));
    assertThat(a2, is(a1));

    // Transitive (a1 == a2, a2 == a3 ⇒ a1 == a3)
    IdentifiableObject a3 = wrap("foo");
    assertThat(a2, is(a3));
    assertThat(a1, is(a3));

    // Consistent hashCode for equal objects
    assertThat(a1.hashCode(), is(a2.hashCode()));
    assertThat(a2.hashCode(), is(a3.hashCode()));

    // Unequal objects differ
    assertThat(a1, is(not(b)));
  }

  // ========================== toString ==========================

  @Test
  public void testToString() {
    Object obj = "Brand new string";
    int hash = System.identityHashCode(obj);
    ObjectRef key = ObjectRef.from(REF_COUNTER.getAndIncrement());

    IdentifiableObject identifiableObject =
        new IdentifiableObject(obj, key, new ReferenceQueue<>());

    assertThat(
        identifiableObject.toString(),
        is("IdentifiableObject{key=" + key + ", hash=" + hash + '}'));
  }
}
