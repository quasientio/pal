/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.WrappingTestBase;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
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
