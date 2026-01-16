/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class CallMessageIT extends AbstractColferRpcMessageIT {

  private static final String CLASS_NAME = "io.quasient.pal.apps.quantized.rpc.Methods";
  private static final String[] EMPTY_STRING_ARRAY = {};
  private static final Object[] EMPTY_OBJECT_ARRAY = {};

  public CallMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void callClassMethod_privateWithArg_void() {
    String methodName = "testVoidStatic";
    String[] parameterTypes = {"java.lang.String"};
    Object[] parameters = {"Hello from a unit test"};

    callVoidClassMethod(
        CLASS_NAME, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_privateWithPrimitiveAndWrapperArgs_void() {
    String methodName = "printArg";
    String[] parameterTypes = {"int", "java.lang.String"};
    Object[] parameters = {2, "more than an argument"};

    callVoidClassMethod(
        CLASS_NAME, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_packageWithNoArgs_void() {
    String methodName = "doSomethingStatically";

    callVoidClassMethod(
        CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);
  }

  @Test
  public void callClassMethod_publicStaticVoidMain_void() {
    String methodName = "main";
    String[] parameterTypes = {"[Ljava.lang.String;"};
    Object[] parameters = {new String[] {}};

    callVoidClassMethod(
        CLASS_NAME, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_withObjectrefAsArg_void() throws Exception {
    String methodName = "sumUpList";

    ReturnValue listReturnValue = callEmptyConstructor("java.util.ArrayList");
    ObjectRef listObjRef = ObjectRef.from(listReturnValue.getObject().getRef());

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

    String[] parameterTypes = {"java.util.ArrayList"};
    Object[] parameters = new Object[1];
    ObjectRef[] paramObjRefs = {listObjRef};

    callVoidClassMethod(CLASS_NAME, methodName, parameterTypes, parameters, paramObjRefs);
  }

  @Test
  public void callClassMethod_noSuchClass_exThrown() {
    String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    String methodName = "doSomethingStatically";

    callVoidClassMethod(
        nonExistingClass,
        methodName,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0],
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void callClassMethod_noSuchMethod_exThrown() {
    String methodName = "a_made_up_method";

    callVoidClassMethod(
        CLASS_NAME,
        methodName,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0],
        "java.lang.NoSuchMethodException");
  }

  @Test
  public void callClassMethod_throwsRuntimeEx_exThrown() {
    String methodName = "throwRuntimeException";

    callVoidClassMethod(
        CLASS_NAME,
        methodName,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0],
        "java.lang.RuntimeException");
  }

  @Test
  public void callClassMethod_privateWithArg_retValue() throws Exception {
    String methodName = "testNonVoidStatic";
    String param = "GIVE ME THIS IN LOWERCASE";
    String[] parameterTypes = {param.getClass().getName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    ReturnValue retValue =
        callClassMethod(CLASS_NAME, methodName, parameterTypes, parameters, paramObjRefs);

    String shouldReturn = param.toLowerCase(Locale.getDefault());
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_protectedNoArgs_retValue() throws Exception {
    String methodName = "highFive";
    ObjectRef[] paramObjRefs = new ObjectRef[0];

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, paramObjRefs);

    Integer shouldReturn = 5;
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_returnsIntegerSum_retValue() throws Exception {
    String methodName = "nonVoidSumUpList";

    ReturnValue listReturnValue = callEmptyConstructor("java.util.ArrayList");
    ObjectRef listObjRef = ObjectRef.from(listReturnValue.getObject().getRef());

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

    String[] parameterTypes = {"java.util.List"};
    Object[] parameters = new Object[1];
    ObjectRef[] paramObjRefs = {listObjRef};

    ReturnValue retValue =
        callClassMethod(CLASS_NAME, methodName, parameterTypes, parameters, paramObjRefs);

    Integer shouldReturn = Arrays.stream(someIntegers).sum();
    assertValueIsObjectOfType(retValue, Integer.class.getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_usingCollectionsReturnsIntegerSum_retValue() throws Exception {
    final String methodName = "nonVoidSumUpList";

    // 1. Create a new ArrayList instance
    ReturnValue listReturnValue = callEmptyConstructor("java.util.ArrayList");
    ObjectRef listObjRef = ObjectRef.from(listReturnValue.getObject().getRef());

    // 2. Add integers to the list as an array using Collections.addAll
    Integer[] someIntegers = {39, 5, 58, 32, 70, 42}; // needs to be Integer[], not int[]
    String[] parameterTypes = new String[] {"java.util.List", someIntegers.getClass().getName()};
    Object[] parameters = new Object[] {null, someIntegers};
    ObjectRef[] paramObjRefs = {listObjRef, null};
    callClassMethod("java.util.Collections", "addAll", parameterTypes, parameters, paramObjRefs);

    // 3. Call the sum up method
    parameterTypes = new String[] {"java.util.List"};
    parameters = new Object[] {null};
    paramObjRefs = new ObjectRef[] {listObjRef};
    ReturnValue retValue =
        callClassMethod(CLASS_NAME, methodName, parameterTypes, parameters, paramObjRefs);

    assertValueIsObjectOfType(retValue, Integer.class.getName());
    Integer shouldReturn = Arrays.stream(someIntegers).mapToInt(Integer::intValue).sum();
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_returningNullObject_nullRetValue() throws Exception {
    String methodName = "giveMeNull";

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    assertValueIsNullObjectOfType(retValue, "java.lang.Object");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertNull(rawObj);
  }

  @Test
  public void callClassMethod_returningCharArray_retValue() throws Exception {
    String methodName = "toCharArray";
    String param = "split me up";
    String[] parameterTypes = {param.getClass().getName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    ReturnValue retValue =
        callClassMethod(CLASS_NAME, methodName, parameterTypes, parameters, paramObjRefs);

    char[] shouldReturn = param.toCharArray();
    assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (char[]) rawObj);
  }

  @Test
  public void callClassMethod_returningEmptyArray_retValue() throws Exception {
    String methodName = "giveMeAnEmptyLongArray";

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    Long[] shouldReturn = {};
    assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (Long[]) rawObj);
  }

  @Test
  public void callClassMethod_returningNullArray_nullRetValue() throws Exception {
    String methodName = "giveMeNullBoolArray";

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Boolean;");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertNull(rawObj);
  }

  @Test
  public void callClassMethod_returningObjectRef_refRetValue() throws Exception {
    String methodName = "getThreadSingleton";

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    assertValueIsObjectRefOfType(retValue, "java.lang.Thread");
    int firstRef = retValue.getObject().getRef();

    retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    assertValueIsObjectRefOfType(retValue, "java.lang.Thread");
    int secondRef = retValue.getObject().getRef();
    assertEquals(firstRef, secondRef);
  }

  @Test
  public void callClassMethod_returningObjectRefArray_refRetValue() throws Exception {
    String methodName = "getThreadArray";

    ReturnValue retValue =
        callClassMethod(
            CLASS_NAME, methodName, EMPTY_STRING_ARRAY, EMPTY_OBJECT_ARRAY, new ObjectRef[0]);

    assertValueIsArrayOfType(retValue, "[Ljava.lang.Thread;");
  }

  @Test
  public void callClassMethod_badFormat_exThrown() {
    String methodName = "parseInt";
    String param = "not_a_num";
    String[] parameterTypes = {param.getClass().getTypeName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callClassMethod(
        "java.lang.Integer",
        methodName,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.NumberFormatException");
  }

  @Test
  public void callClassMethod_throwsEx_exThrown() {
    String methodName = "throwMeAnException";

    callClassMethod(
        CLASS_NAME,
        methodName,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0],
        "java.lang.RuntimeException");
  }

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_void() throws Exception {
    String methodName = "doSomething";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    callVoidInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0]);
  }

  @Test
  public void callInstanceMethod_privateWithArg_void() throws Exception {
    String methodName = "testArg";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    String param = "testing testing 1 2 3";
    String[] parameterTypes = {param.getClass().getName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callVoidInstanceMethod(
        CLASS_NAME, methodName, newObjRef, parameterTypes, parameters, paramObjRefs);
  }

  @Test
  public void callInstanceMethod_protectedNoArgs_void() throws Exception {
    String methodName = "printDate";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    callVoidInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0]);
  }

  @Test
  public void callInstanceMethod_nullArg_throwsEx() throws Exception {
    String methodName = "testNonNullArg";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    String[] parameterTypes = {String.class.getName()};
    Object[] parameters = {null};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callVoidInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.NullPointerException");
  }

  @Test
  public void callInstanceMethod_noSuchClass_throwsEx() throws Exception {
    String nonExistingClass = "io.quasient.pal.apps.IDontExist";
    String methodName = "testNonNullArg";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    String[] parameterTypes = {String.class.getName()};
    Object[] parameters = {null};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callVoidInstanceMethod(
        nonExistingClass,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void callInstanceMethod_noSuchMethod_throwsEx() throws Exception {
    String methodName = "a_made_up_method";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    String[] parameterTypes = {String.class.getName()};
    Object[] parameters = {null};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callVoidInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.NoSuchMethodException");
  }

  @Test
  public void callInstanceMethod_noSuchInstance_throwsNullPointerException() {
    String methodName = "printDate";

    ObjectRef newObjRef = ObjectRef.from("2398248");

    callVoidInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        EMPTY_STRING_ARRAY,
        EMPTY_OBJECT_ARRAY,
        new ObjectRef[0],
        "java.lang.NullPointerException");
  }

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_retValue() throws Exception {
    String methodName = "giveMeX";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    ReturnValue retValue =
        callInstanceMethod(
            CLASS_NAME,
            methodName,
            newObjRef,
            EMPTY_STRING_ARRAY,
            EMPTY_OBJECT_ARRAY,
            new ObjectRef[0]);

    Integer shouldReturn = 4;
    assertValueIsObjectOfType(retValue, Integer.class.getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_publicReturnsListAsRef_retValue() throws Exception {
    String methodName = "getListOfStrings";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    ReturnValue retValue =
        callInstanceMethod(
            CLASS_NAME,
            methodName,
            newObjRef,
            EMPTY_STRING_ARRAY,
            EMPTY_OBJECT_ARRAY,
            new ObjectRef[0]);

    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_publicReturnsNativelyInitListAsRef_retValue() throws Exception {
    String methodName = "getListOfStringsShorthand";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    ReturnValue retValue =
        callInstanceMethod(
            CLASS_NAME,
            methodName,
            newObjRef,
            EMPTY_STRING_ARRAY,
            EMPTY_OBJECT_ARRAY,
            new ObjectRef[0]);

    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_withObjectsAndObjectrefsAsArgs_retValue() throws Exception {
    String methodName = "addOffsetToListAndSumUp";

    ReturnValue listReturnValue = callEmptyConstructor("java.util.ArrayList");
    ObjectRef listObjRef = ObjectRef.from(listReturnValue.getObject().getRef());

    int[] someIntegers = {1, 2, 3, 5, 7, 9};
    for (int someInt : someIntegers) {
      callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          new String[] {"java.lang.Integer"},
          new Object[] {someInt},
          new ObjectRef[] {null});
    }

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    String[] parameterTypes = {"int", "java.util.ArrayList"};
    int offsetParam = 10;
    Object[] parameters = {offsetParam, null};
    ObjectRef[] paramObjRefs = {null, listObjRef};

    ReturnValue retValue =
        callInstanceMethod(
            CLASS_NAME, methodName, newObjRef, parameterTypes, parameters, paramObjRefs);

    Integer shouldReturn = Arrays.stream(someIntegers).map(i -> i + offsetParam).sum();
    assertValueIsObjectOfType(retValue, Integer.class.getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_throwsCheckedException_exThrown() throws Exception {
    String methodName = "throwsCheckedException";

    ReturnValue instanceReturnValue = callEmptyConstructor(CLASS_NAME);
    ObjectRef newObjRef = ObjectRef.from(instanceReturnValue.getObject().getRef());

    Object param = (long) Integer.MAX_VALUE + 1;
    String[] parameterTypes = {param.getClass().getTypeName()};
    Object[] parameters = {param};
    ObjectRef[] paramObjRefs = new ObjectRef[1];

    callInstanceMethod(
        CLASS_NAME,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        paramObjRefs,
        "java.lang.Exception");
  }
}
