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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.Unwrapper;
import net.ittera.pal.serdes.WrappingTestBase;
import org.junit.Test;

// Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
public class UnwrapperTest extends WrappingTestBase {

  @Test
  public void unwrapObject_wrappedNullObject_originalValue() throws ClassNotFoundException {
    Obj wrappedObj = Wrapper.getWrappedObject(null, "java.lang.Float", null);
    assertNull(Unwrapper.unwrapObject(wrappedObj));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unwrapObject_wrappedVoidObject_illegalArgumentException()
      throws ClassNotFoundException {
    Object wrappable = void.class;
    Obj wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass().getName(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test(expected = IllegalArgumentException.class)
  public void unwrapObject_wrappedVoidClass_illegalArgumentException()
      throws ClassNotFoundException {
    Object wrappable = Void.class;
    Obj wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass().getName(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test
  public void unwrapObject_stringBuilder_originalValue() throws ClassNotFoundException {
    StringBuilder wrappable = new StringBuilder("world");
    Obj wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass().getName(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);

    // compare class and value
    assertEquals(wrappable.getClass(), unwrapped.getClass());
    assertEquals(wrappable.toString(), unwrapped.toString());
  }

  @Test
  @SuppressWarnings("JdkObsolete") // silence errorprone warnings about StringBuffer
  public void unwrapObject_stringBuffer_originalValue() throws ClassNotFoundException {
    StringBuffer wrappable = new StringBuffer("world");
    Obj wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass().getName(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);

    // compare class and value
    assertEquals(wrappable.getClass(), unwrapped.getClass());
    assertEquals(wrappable.toString(), unwrapped.toString());
  }

  @Test
  public void unwrapObject_valuedWrappable_originalValue() throws ClassNotFoundException {

    // test all wrappables with value except charSeq's
    List<Object> valuedWrappableObjs =
        wrappableObjects.stream()
            .filter(o -> o != null && o != void.class && o != Void.class)
            .toList();

    for (Object wrappable : valuedWrappableObjs) {
      Obj wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass().getName(), null);
      Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
      // compare class and value(s)
      assertEquals(wrappable.getClass(), unwrapped.getClass());
      if (wrappable.getClass().isArray()) {
        myAssertArrayEquals(wrappable, unwrapped);
      } else if (wrappable instanceof CharSequence) {
        assertEquals(wrappable.toString(), unwrapped.toString());
      } else {
        assertEquals(wrappable, unwrapped);
      }
    }
  }

  @Test
  public void unwrapObject_TypeIsNullObjectIsString_returnsString() {
    Obj wrappedObj = new Obj();
    wrappedObj.setValue("Hiya");
    wrappedObj.setClazz(new net.ittera.pal.messages.colfer.Class().withName("java.lang.String"));
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj, null);
    assertNotNull(unwrapped);
    assertEquals(String.class, unwrapped.getClass());
  }
}
