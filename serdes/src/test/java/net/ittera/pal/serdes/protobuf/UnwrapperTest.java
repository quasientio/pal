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

package net.ittera.pal.serdes.protobuf;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.util.List;
import net.ittera.pal.messages.protobuf.Primitives;
import net.ittera.pal.serdes.WrappingTestBase;
import org.junit.Test;

public class UnwrapperTest extends WrappingTestBase {

  @Test
  public void unwrapObject_wrappedNullObject_originalValue() throws ClassNotFoundException {

    Float wrappable = null;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, "java.lang.Float", null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(wrappable, unwrapped);
  }

  @Test
  public void unwrapObject_wrappedVoidObject_originalValue() throws ClassNotFoundException {

    Object wrappable = void.class;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test
  public void unwrapObject_wrappedVoidClass_originalValue() throws ClassNotFoundException {

    Object wrappable = Void.class;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test
  public void unwrapObject_stringBuilder_originalValue() throws ClassNotFoundException {

    StringBuilder wrappable = new StringBuilder("world");
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);

    // compare class and value
    assertEquals(wrappable.getClass(), unwrapped.getClass());
    assertEquals(wrappable.toString(), unwrapped.toString());
  }

  @Test
  public void unwrapObject_stringBuffer_originalValue() throws ClassNotFoundException {

    StringBuffer wrappable = new StringBuffer("world");
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
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
            .filter(o -> !Wrapper.reconstructableCharSeqClasses.contains(o.getClass()))
            .collect(toList());

    for (Object wrappable : valuedWrappableObjs) {
      Primitives.Object wrappedObj =
          Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
      Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
      // compare class and value
      assertEquals(wrappable.getClass(), unwrapped.getClass());
      assertEquals(wrappable, unwrapped);
    }
  }

  // TODO Arrays (include array of null's and void's)
}
