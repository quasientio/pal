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

package net.ittera.pal.rpc.binary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import org.junit.Test;

public class ControlMessageIT extends AbstractBinaryRpcMessageIT {
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
