/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.hamcrest.Matchers;
import org.junit.Test;

public class IdentifiableObjectTest {

  @Test
  public void testConstructor() {
    new IdentifiableObject("one, two, three");
    new IdentifiableObject(Arrays.asList("one", "two", "three"));

    try {
      new IdentifiableObject(null);
      fail("Should have raised NPE.");
    } catch (NullPointerException ignored) {
      // all good
    }
  }

  @Test
  public void testHashCode() {
    List<Float> floats = Arrays.asList(10f, 11f, 12f);
    int identityHash = System.identityHashCode(floats);
    assertThat(new IdentifiableObject(floats).hashCode(), is(identityHash));
  }

  @Test
  public void testEquals() {
    // different Integer instances, but with same value should be equal
    Integer int1 = 23;
    Integer int2 = 23;
    assertThat(new IdentifiableObject(int1), Matchers.is(new IdentifiableObject(int2)));

    // different instances should not be equal
    String str1 = "ABC";
    String str2 = new String("ABC");
    assertThat(new IdentifiableObject(str1), is(Matchers.not(new IdentifiableObject(str2))));

    List<Float> floats1 = Arrays.asList(10f, 11f, 12f);
    List<Float> floats2 = Arrays.asList(10f, 11f, 12f);
    assertThat(new IdentifiableObject(floats1), is(Matchers.not(new IdentifiableObject(floats2))));

    // equal instances must be equal
    assertThat(new IdentifiableObject(23), Matchers.is(new IdentifiableObject(23)));
    assertThat(new IdentifiableObject("ABC"), Matchers.is(new IdentifiableObject("ABC")));
    assertThat(new IdentifiableObject(floats1), Matchers.is(new IdentifiableObject(floats1)));
    assertThat(new IdentifiableObject(Void.class), Matchers.is(new IdentifiableObject(Void.class)));

    // equals with non-IdentifiableObject
    assertThat(new IdentifiableObject(int2), is(not(int2)));
  }

  @Test
  public void equalsContract() {
    WeakReference<Object> objectWeakReference = new WeakReference<>("Im an object");
    WeakReference<Object> objectWeakReference2 = new WeakReference<>("Im an object");

    EqualsVerifier.forClass(IdentifiableObject.class)
        .withPrefabValues(WeakReference.class, objectWeakReference, objectWeakReference2)
        .withIgnoredFields("object")
        .verify();
  }

  @Test
  public void testToString() {
    Object object = new String("Brand new string");
    WeakReference<Object> objectWeakReference = new WeakReference<>(object);
    IdentifiableObject identifiableObject = new IdentifiableObject(object);
    int hash = System.identityHashCode(object);

    assertThat(
        identifiableObject.toString(),
        is("IdentifiableObject{" + "object=" + objectWeakReference.get() + ", hash=" + hash + '}'));
  }
}
