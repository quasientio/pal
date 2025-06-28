/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.binary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.colfer.ColferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Helper assertion methods. Encapsulates details of serialization.
public interface ExecMessageAssertions {

  Logger logger = LoggerFactory.getLogger("tests");

  default void assertIsObjectOfType(
      ReturnValue returnValue, String className, boolean hasObjRef, boolean isNull, boolean isArray)
      throws ClassNotFoundException {
    logger.trace(
        "in assertIsObjectOfType w/: returnValue:\n{}"
            + ", className: {}, hasObjRef: {}, getIsNull: {}, isArray: {}",
        returnValue,
        className,
        hasObjRef,
        isNull,
        isArray);

    assertFalse(returnValue.getIsVoid());
    assertThat(returnValue.getObject(), is(not(nullValue())));

    Obj retObj = returnValue.getObject();
    assertEquals(isArray, isArrayClassName(retObj.getClazz().getName()));
    assertEquals(isNull, retObj.getIsNull());
    assertEquals(hasObjRef, retObj.getRef() != null && !retObj.getRef().isEmpty());
    assertThat(retObj.getClazz(), is(not(nullValue())));

    // className can be equal or represent a class that is a superclass of the actual class
    if (!className.equals(retObj.getClazz().getName())) {
      Class<?> expectedClazz = Class.forName(className);
      Class<?> returnedClazz = Class.forName(retObj.getClazz().getName());
      assertTrue(expectedClazz.isAssignableFrom(returnedClazz));
    } else {
      assertEquals(className, retObj.getClazz().getName());
    }
  }

  default void assertValueIsObjectOfType(ReturnValue returnValue, String className)
      throws ClassNotFoundException {
    assertIsObjectOfType(returnValue, className, true, false, false);
  }

  default void assertValueIsObjectRefOfType(ReturnValue returnValue, String className)
      throws ClassNotFoundException {
    assertIsObjectOfType(returnValue, className, true, false, false);
  }

  default void assertValueIsArrayOfType(ReturnValue returnValue, String className)
      throws ClassNotFoundException {
    assertIsObjectOfType(returnValue, className, true, false, true);
  }

  default void assertValueIsNullObjectOfType(ReturnValue returnValue, String className)
      throws ClassNotFoundException {
    assertIsObjectOfType(returnValue, className, false, true, false);
  }

  default void assertValueIsNullArrayOfType(ReturnValue returnValue, String className)
      throws ClassNotFoundException {
    assertIsObjectOfType(returnValue, className, false, true, true);
  }

  private static boolean isArrayClassName(String className) {
    return className.startsWith("[");
  }

  @SuppressWarnings("unchecked")
  default <T> void assertValueEqualsArray(T[] actualArray, ReturnValue retValue) throws Exception {
    logger.trace(
        "in assertValueEqualsArray w/: actualArray: {}, retValue:\n{}", actualArray, retValue);

    // check array type
    Class<?> classOfArray = actualArray.getClass();
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(classOfArray.isInstance(rawObj));

    // check length
    assertEquals(actualArray.length, ((T[]) rawObj).length);

    // check contents equal
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((T[]) rawObj)[i]);
    }
  }

  default void assertHasThrowableOfType(ExecMessage msg, String throwableType) {
    logger.trace(
        "in assertHasThrowableOfType w/: msg:\n{}, throwableType: {}",
        ColferUtils.format(msg),
        throwableType);

    assertThat(msg.getRaisedThrowable(), is(not(nullValue())));
    assertEquals(throwableType, msg.getRaisedThrowable().getThrowable().getType());
  }
}
