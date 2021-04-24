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

import static org.junit.Assert.assertEquals;

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: - arrays
 */
public class NonVoidInstanceMethodMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.rmi.explicit.NonVoidInstanceMethods";

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_retValue() throws Exception {

    String methodName = "giveMeX";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    Integer shouldReturn = 4;
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_publicReturnsListAsRef_retValue() throws Exception {

    String methodName = "getListOfStrings";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_publicReturnsNativelyInitListAsRef_retValue() throws Exception {
    String methodName = "getListOfStringsShorthand";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_withObjectsAndObjectrefsAsArgs_retValue() throws Exception {

    String methodName = "addOffsetToListAndSumUp";

    // new ArrayList<Integer>
    ObjectRef listObjRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add some int's to the list
    int[] someInts = {1, 2, 3, 5, 7, 9};
    for (int someInt : someInts) {
      callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          new String[] {"java.lang.Integer"},
          new Object[] {someInt},
          new ObjectRef[someInts.length]);
    }

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // prepare parameters, expected return value
    String[] parameterTypes = {"int", "java.util.ArrayList"};
    int offsetParam = 10;
    Object[] parameters = {offsetParam, null};
    ObjectRef[] paramObjRefs = {null, listObjRef};

    // now call the method
    ReturnValue retValue =
        callInstanceMethod(
            className, methodName, newObjRef, parameterTypes, parameters, paramObjRefs);

    // test returned value
    Integer shouldReturn = 0;
    for (int someInt : someInts) {
      shouldReturn += someInt + offsetParam;
    }
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_throwsCheckedException_exThrown() throws Exception {

    String methodName = "throwMeACheckedException";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());
    Object param = new Long(Integer.MAX_VALUE) + 1;

    // now call the method
    String[] parameterTypes = {param.getClass().getTypeName()};
    Object[] parameters = {param};

    callInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.Exception");
  }
}
