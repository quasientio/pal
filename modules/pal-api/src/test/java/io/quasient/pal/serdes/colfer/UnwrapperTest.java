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
package io.quasient.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.WrappingTestBase;
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
