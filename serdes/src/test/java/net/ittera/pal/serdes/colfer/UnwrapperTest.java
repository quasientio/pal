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

package net.ittera.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.Unwrapper;
import net.ittera.pal.serdes.WrappingTestBase;
import org.junit.Test;

// Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
public class UnwrapperTest extends WrappingTestBase {

  @Test
  public void unwrapObject_wrappedNullObject_originalValue() throws ClassNotFoundException {
    Obj wrappedObj =
        Wrapper.getWrappedObject(null, "java.lang.Float", null, WrapPolicy.PREFER_REFERENCE);
    assertNull(Unwrapper.unwrapObject(wrappedObj));
  }

  @Test
  public void unwrapObject_valuedWrappable_originalValue() throws ClassNotFoundException {

    List<Object> valuedWrappableObjs = wrappableObjects.stream().filter(Objects::nonNull).toList();

    for (Object wrappable : valuedWrappableObjs) {
      Obj wrappedObj =
          Wrapper.getWrappedObject(
              wrappable, wrappable.getClass().getName(), null, WrapPolicy.PREFER_REFERENCE);
      Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
      // compare class and value(s)
      assertEquals(wrappable.getClass(), unwrapped.getClass());
      if (wrappable.getClass().isArray()) {
        assertEquals(Array.getLength(wrappable), Array.getLength(unwrapped));
        for (int i = 0; i < Array.getLength(wrappable); i++) {
          assertEquals(Array.get(wrappable, i).toString(), Array.get(unwrapped, i).toString());
        }
      } else {
        assertEquals(wrappable, unwrapped);
      }
    }
  }
}
