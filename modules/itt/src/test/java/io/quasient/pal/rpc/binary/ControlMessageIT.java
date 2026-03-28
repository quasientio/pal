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
package io.quasient.pal.rpc.binary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ReturnValue;
import javax.annotation.Nullable;
import org.junit.Test;

public class ControlMessageIT extends AbstractColferRpcMessageIT {
  private ReturnValue addToList(ObjectRef listObjRef, String value) {
    return addToList(listObjRef, value, null);
  }

  private ReturnValue addToList(
      ObjectRef listObjRef, String arg, @Nullable String expectedThrowableType) {
    Object[] parameters = {arg};
    String[] parameterTypes = {arg.getClass().getName()};
    if (expectedThrowableType == null) {
      return callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          parameterTypes,
          parameters,
          new ObjectRef[parameterTypes.length]);
    } else {
      return callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          parameterTypes,
          parameters,
          new ObjectRef[parameterTypes.length],
          expectedThrowableType);
    }
  }

  @Test
  public void deleteObjectFromSession() throws Exception {
    ObjectRef arrayListRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add a string to the list
    ReturnValue returnValue = addToList(arrayListRef, "testing testing 1 2 3");
    assertValueIsObjectOfType(returnValue, "boolean");

    // now delete object from session
    assertTrue(sendDeleteObjectCommand(arrayListRef));

    // try to add another value, expect an NPE to be thrown
    addToList(arrayListRef, "testing testing 4 5 6", "java.lang.NullPointerException");
  }

  @Test
  public void deleteSession() throws Exception {
    ObjectRef arrayListRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add a string to the list
    ReturnValue returnValue = addToList(arrayListRef, "testing testing 1 2 3");
    assertValueIsObjectOfType(returnValue, "boolean");

    // now delete peer session
    assertTrue(sendDeleteSessionCommand());

    // try to add another value, expect an NPE to be thrown
    addToList(arrayListRef, "testing testing 4 5 6", "java.lang.NullPointerException");
  }

  @Test
  public void testGC() throws Exception {
    boolean result = sendGcCommand();
    assertThat(result, is(true));
  }
}
