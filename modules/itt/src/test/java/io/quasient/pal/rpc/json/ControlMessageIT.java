/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
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
