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
import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class ObjectRefTest {

  @Before
  public void setUp() {}

  @Test
  public void fromAsString() {
    ObjectRef objectRef = ObjectRef.from("23");
    assertEquals(23, objectRef.getRef());
  }

  @Test
  public void fromAsInt() {
    ObjectRef objectRef = ObjectRef.from(23);
    assertEquals(23, objectRef.getRef());
  }

  @Test
  public void testAsString() {
    ObjectRef objectRef = ObjectRef.from(23);
    assertEquals("23", objectRef.asString());
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(ObjectRef.class).usingGetClass().verify();
  }

  @Test
  public void testToString() {
    int ref = 17;
    ObjectRef objectRef = ObjectRef.from(ref);
    assertThat(objectRef.toString(), is("objectRef: {" + ref + '}'));
  }
}
