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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Locale;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior.
 *
 * <p>TODO: - returningObjectRefArray() commented out below is failing. Add @Test, make it public
 * and fix.
 */
public class NonVoidClassMethodMessageIT extends AbstractBinaryRPCMessageIT {

  protected final String className = "net.ittera.pal.apps.rpc.NonVoidStaticMethods";

  @Test
  public void callClassMethod_privateWithArg_retValue() throws Exception {

    String methodName = "testNonVoidStatic";

    String param = "GIVE ME THIS IN LOWERCASE";
    Object[] parameters = {param};
    String[] parameterTypes = {param.getClass().getName()};
    ObjectRef[] paramObjRefs = new ObjectRef[parameters.length];

    ReturnValue retValue =
        callClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);

    // test returned value
    String shouldReturn = param.toLowerCase(Locale.getDefault());
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_protectedNoArgs_retValue() throws Exception {

    String methodName = "highFive";

    String[] parameterTypes = {};
    Object[] parameters = {};
    ObjectRef[] paramObjRefs = new ObjectRef[parameters.length];
    ReturnValue retValue =
        callClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);

    // test returned value
    Integer shouldReturn = 5;
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_returnsIntegerSum_retValue() throws Exception {

    String methodName = "nonVoidSumUpList";

    // new ArrayList<Integer>
    ObjectRef listObjRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add some integers
    int[] someIntegers = {39, 5, 58, 32, 70, 42};
    for (int someInt : someIntegers) {
      callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          new String[] {"java.lang.Integer"},
          new Object[] {someInt},
          new ObjectRef[] {null});
    }

    // call method
    String[] parameterTypes = {"java.util.List"};
    Object[] params = new Object[parameterTypes.length];
    ObjectRef[] objRefs = {listObjRef};
    ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, params, objRefs);

    // test returned value
    Integer shouldReturn = Arrays.stream(someIntegers).reduce(0, Integer::sum);
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_returningNullObject_nullRetValue() throws Exception {

    String methodName = "giveMeNull";

    // call method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsNullObjectOfType(retValue, "java.lang.Object");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertNull(rawObj);
  }

  @Test
  public void callClassMethod_returningCharArray_retValue() throws Exception {

    String methodName = "toCharArray";

    // call method
    String param = "split me up";
    String[] parameterTypes = {param.getClass().getName()};
    Object[] parameters = {param};
    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    // test returned value
    char[] shouldReturn = param.toCharArray();
    assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (char[]) rawObj);
  }

  @Test
  public void callClassMethod_returningEmptyArray_retValue() throws Exception {

    String methodName = "giveMeAnEmptyLongArray";

    // call method
    String[] parameterTypes = {};
    Object[] parameters = {};
    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    // test returned value
    Long[] shouldReturn = {};
    assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (Long[]) rawObj);
  }

  @Test
  public void callClassMethod_returningNullArray_nullRetValue() throws Exception {

    String methodName = "giveMeNullBoolArray";

    // call method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Boolean;");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(null, (Boolean[]) rawObj);
  }

  @Test
  public void callClassMethod_returningObjectRef_refRetValue() throws Exception {

    String methodName = "getThreadSingleton";

    String[] parameterTypes = {};
    Object[] parameters = {};

    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.lang.Thread");

    // because field is a singleton, with a 2nd call we should get the same instance objectRef,
    // let's make sure
    String appRef = retValue.getObject().getRef();

    retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            parameters,
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.lang.Thread");
    String secondAppRef = retValue.getObject().getRef();
    assertEquals(appRef, secondAppRef);
  }

  // TODO reinstate and fix if it doesn't pass
  private void callClassMethod_returningObjectRefArray_refRetValue() throws Exception {

    String methodName = "getThreadArray";

    String[] parameterTypes = {};

    ReturnValue retValue =
        callClassMethod(
            className,
            methodName,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    assertValueIsArrayOfType(retValue, String.format("[L%s;", "java.lang.Thread"));
  }

  @Test
  public void callClassMethod_badFormat_exThrown() throws Exception {

    String methodName = "parseInt";
    String param = "not_a_num";

    String[] parameterTypes = {param.getClass().getTypeName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[parameters.length];

    callClassMethod(
        "java.lang.Integer",
        methodName,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.NumberFormatException");
  }

  @Test
  public void callClassMethod_throwsEx_exThrown() throws Exception {

    String methodName = "throwMeAnException";

    String[] parameterTypes = {};
    Object[] parameters = {};
    ObjectRef[] paramObjRefs = new ObjectRef[parameters.length];

    callClassMethod(
        className,
        methodName,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.RuntimeException");
  }
}
