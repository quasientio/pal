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

package net.ittera.pal.rmi.explicit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecMessageAssertions {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  /**
   * Helper assertion methods. Encapsulates details of serialization.
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
    logger.trace(
        "in assertIsObjectOfType w/: returnValue:\n{}"
            + ", className: {}, hasObjRef: {}, isNull: {}, isArray: {}",
        returnValue,
        className,
        hasObjRef,
        isNull,
        isArray);

    assertFalse(returnValue.getIsVoid());
    assertFalse(returnValue.getIsClass());
    assertThat(returnValue.getClazz(), is(not(nullValue())));
    assertEquals(className, returnValue.getObject().getClazz().getName());
    assertThat(returnValue.getObject(), is(not(nullValue())));

    Obj retObj = returnValue.getObject();
    assertEquals(isArray, retObj.getIsArray());
    assertEquals(isNull, retObj.getIsNull());
    assertEquals(hasObjRef, retObj.getRef() != null && !retObj.getRef().isEmpty());
    assertThat(retObj.getClazz(), is(not(nullValue())));
    assertFalse(retObj.getClazz().getUnknown());
    assertEquals(className, retObj.getClazz().getName());
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
    logger.trace(
        "in assertValueEqualsArray w/: actualArray: {}, retValue:\n{}", actualArray, retValue);

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
    logger.trace(
        "in assertHasThrowableOfType w/: msg:\n{}, throwableType: {}",
        ColferUtils.format(msg),
        throwableType);

    assertThat(msg.getRaisedThrowable(), is(not(nullValue())));
    assertEquals(throwableType, msg.getRaisedThrowable().getThrowable().getType());
  }
}
