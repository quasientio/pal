/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
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
import org.junit.Test;

public class IdentifiableObjectTest {

  /* ------------------------------------------------------------------ */
  /*  helper: create wrapper that mirrors store’s production logic      */
  /* ------------------------------------------------------------------ */
  private IdentifiableObject wrap(Object o) {
    return new IdentifiableObject(
        o, ObjectRef.from(String.valueOf(System.identityHashCode(o))), new ReferenceQueue<>());
  }

  /* ------------------------------------------------------------------ */
  @Test
  public void testConstructor() {
    wrap("one, two, three");
    wrap(Arrays.asList("one", "two", "three"));

    try {
      new IdentifiableObject(null, null, new ReferenceQueue<>());
      fail("Should have raised NPE.");
    } catch (NullPointerException ignored) {
      // expected
    }
  }

  /* ------------------------------------------------------------------ */
  @Test
  public void testHashCode() {
    List<Float> floats = Arrays.asList(10f, 11f, 12f);
    int identityHash = System.identityHashCode(floats);
    assertThat(wrap(floats).hashCode(), is(identityHash));
  }

  /* ------------------------------------------------------------------ */
  @Test
  public void testEquals() {
    // same value, different identity – should be equal (same identityHashCode for cached Integer)
    Integer int1 = 23;
    Integer int2 = 23;
    assertThat(wrap(int1), is(wrap(int2)));

    // different instances with same value should NOT be equal
    String str1 = "ABC";
    String str2 = new String("ABC");
    assertThat(wrap(str1), is(not(wrap(str2))));

    List<Float> floats1 = Arrays.asList(10f, 11f, 12f);
    List<Float> floats2 = Arrays.asList(10f, 11f, 12f);
    assertThat(wrap(floats1), is(not(wrap(floats2))));

    // equal instances must be equal
    assertThat(wrap(23), is(wrap(23)));
    assertThat(wrap("ABC"), is(wrap("ABC")));
    assertThat(wrap(floats1), is(wrap(floats1)));
    assertThat(wrap(Void.class), is(wrap(Void.class)));

    // equals with non-IdentifiableObject
    assertThat(wrap(int2), is(not((Object) int2)));
  }

  /* ------------------------------------------------------------------ */
  @Test
  public void equalsContractManually() {
    IdentifiableObject a1 = wrap("foo");
    IdentifiableObject a2 = wrap("foo"); // same identityHashCode
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

    // Consistent hashCode
    assertThat(a1.hashCode(), is(a2.hashCode()));
    assertThat(a2.hashCode(), is(a3.hashCode()));

    // Unequal objects differ
    assertThat(a1, is(not(b)));
    assertThat(a1.hashCode() == b.hashCode(), is(false));
  }

  /* ------------------------------------------------------------------ */
  @Test
  public void testToString() {
    Object obj = "Brand new string";
    int hash = System.identityHashCode(obj);
    ObjectRef key = ObjectRef.from(String.valueOf(hash));

    IdentifiableObject identifiableObject =
        new IdentifiableObject(obj, key, new ReferenceQueue<>());

    assertThat(
        identifiableObject.toString(),
        is("IdentifiableObject{key=" + key + ", hash=" + hash + '}'));
  }
}
