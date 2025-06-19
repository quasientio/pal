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

package com.quasient.pal.rpc.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import javax.annotation.Nullable;
import org.junit.Test;

public class ControlMessageIT extends AbstractJsonRpcMessageIT {

  private void addToList(ObjectRef listObjRef, String value) throws Exception {
    addToList(listObjRef, value, null);
  }

  private void addToList(ObjectRef listObjRef, String arg, @Nullable String expectedThrowableType)
      throws Exception {
    String argsAsJson =
            """
            [{ "type": "%s", "value": "%s" }]
            """
            .formatted(arg.getClass().getName(), arg);
    JsonRpcResponse response =
        callInstanceMethod(listObjRef.getRef(), "java.util.ArrayList", "add", argsAsJson);
    assertNotNull(response);
    if (expectedThrowableType == null) {
      assertNotNull(response.getResult());
    } else {
      assertNotNull(response.getError());
      assertThat(response.getError().getData().getThrowableType(), is(expectedThrowableType));
    }
  }

  @Test
  public void deleteObjectFromSession() throws Exception {
    ObjectRef arrayListRef = ObjectRef.from(createNewInstance("java.util.ArrayList"));

    // add a string to the list
    addToList(arrayListRef, "testing testing 1 2 3");

    // now delete object from session
    sendDeleteObjectCommand(arrayListRef);

    // try to add another value, expect an NPE to be thrown
    addToList(arrayListRef, "testing testing 4 5 6", "java.lang.NullPointerException");
  }

  @Test
  public void deleteSession() throws Exception {
    ObjectRef arrayListRef = ObjectRef.from(createNewInstance("java.util.ArrayList"));

    // add a string to the list
    addToList(arrayListRef, "testing testing 1 2 3");

    // now delete peer session
    sendDeleteSessionCommand();

    // try to add another value, expect an NPE to be thrown
    addToList(arrayListRef, "testing testing 4 5 6", "java.lang.NullPointerException");
  }

  @Test
  public void testGC() throws Exception {
    boolean result = sendGcCommand();
    assertThat(result, is(true));
  }
}
