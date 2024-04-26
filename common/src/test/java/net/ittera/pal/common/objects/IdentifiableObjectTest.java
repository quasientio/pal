/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.objects;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
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
    assertThat(new IdentifiableObject(int1), is(new IdentifiableObject(int2)));

    // different instances should not be equal
    String str1 = "ABC";
    String str2 = new String("ABC");
    assertThat(new IdentifiableObject(str1), is(not(new IdentifiableObject(str2))));

    List<Float> floats1 = Arrays.asList(10f, 11f, 12f);
    List<Float> floats2 = Arrays.asList(10f, 11f, 12f);
    assertThat(new IdentifiableObject(floats1), is(not(new IdentifiableObject(floats2))));

    // equal instances must be equal
    assertThat(new IdentifiableObject(23), is(new IdentifiableObject(23)));
    assertThat(new IdentifiableObject("ABC"), is(new IdentifiableObject("ABC")));
    assertThat(new IdentifiableObject(floats1), is(new IdentifiableObject(floats1)));
    assertThat(new IdentifiableObject(Void.class), is(new IdentifiableObject(Void.class)));

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
