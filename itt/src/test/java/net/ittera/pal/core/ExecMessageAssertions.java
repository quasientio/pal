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

package net.ittera.pal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Primitives;
import net.ittera.pal.messages.protobuf.Values.ReturnValue;

public class ExecMessageAssertions {

  /**
   * Helper assertion methods. Encapsulates details of the protobuf serialization.
   *
   * @param returnValue
   * @param className
   * @param hasObjRef
   * @param isNull
   * @param isArray
   */
  private void assertIsObjectOfType(
      ReturnValue returnValue,
      String className,
      boolean hasObjRef,
      boolean isNull,
      boolean isArray) {
    assertFalse(returnValue.getIsVoid());
    assertFalse(returnValue.getIsClass());
    assertTrue(returnValue.hasClazz());
    assertEquals(className, returnValue.getObject().getClass_().getName());
    assertTrue(returnValue.hasObject());

    Primitives.Object retObj = returnValue.getObject();
    assertEquals(isArray, retObj.getIsArray());
    assertEquals(isNull, retObj.getIsNull());
    assertEquals(hasObjRef, retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertFalse(retObj.getClass_().getUnknown());
    assertEquals(className, retObj.getClass_().getName());
  }

  protected void assertValueIsObjectOfType(ReturnValue returnValue, String className) {
    assertIsObjectOfType(returnValue, className, true, false, false);
  }

  protected void assertValueIsObjectRefOfType(ReturnValue returnValue, String className) {
    assertIsObjectOfType(returnValue, className, true, false, false);
  }

  protected void assertValueIsArrayOfType(ReturnValue returnValue, String className) {
    assertIsObjectOfType(returnValue, className, true, false, true);
  }

  protected void assertValueIsNullObjectOfType(ReturnValue returnValue, String className) {
    assertIsObjectOfType(returnValue, className, false, true, false);
  }

  protected void assertValueIsNullArrayOfType(ReturnValue returnValue, String className) {
    assertIsObjectOfType(returnValue, className, false, true, true);
  }

  protected <T> void assertValueEqualsArray(T[] actualArray, ReturnValue retValue)
      throws Exception {

    // check array type
    Class classOfArray = actualArray.getClass();
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(classOfArray.isInstance(rawObj));

    // check length
    assertEquals(actualArray.length, ((T[]) rawObj).length);

    // check contents equal
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((T[]) rawObj)[i]);
    }
  }

  protected void assertHasThrowableOfType(ExecMessage msg, String throwableType) {
    assertTrue(msg.hasRaisedThrowable());
    assertEquals(throwableType, msg.getRaisedThrowable().getThrowable().getType());
  }
}
