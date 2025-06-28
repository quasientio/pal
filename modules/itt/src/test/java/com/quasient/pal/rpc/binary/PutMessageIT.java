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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior.
 *
 * <pre>
 *  TODO:
 *   - null value
 *   - private, protected, package-visible
 *   - primitives - arrays - objectrefs
 * </pre>
 */
@RunWith(Parameterized.class)
public class PutMessageIT extends AbstractBinaryRpcMessageIT {

  protected static final String CLASS_NAME = "com.quasient.pal.apps.rpc.Variables";

  public PutMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void putStatic_integerNotNull_ok() throws Exception {

    String fieldName = "aStaticInteger";
    String fieldClassName = "int";

    Integer originalValue = 3000;
    Integer newValue = 3200;

    try {
      // get field
      ReturnValue retValue = callGetStatic(CLASS_NAME, fieldName);

      // test returned (original) value
      Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof Integer);
      assertEquals(originalValue, rawObj);

      // set a new value
      callPutStatic(CLASS_NAME, fieldName, fieldClassName, newValue);

      // get again and test
      retValue = callGetStatic(CLASS_NAME, fieldName);
      assertValueIsObjectOfType(retValue, fieldClassName);
      rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof Integer);
      assertEquals(newValue, rawObj);
    } finally {
      // now revert changed value to original (otherwise other tests may fail after a 1st run)
      callPutStatic(CLASS_NAME, fieldName, fieldClassName, originalValue);
    }
  }

  @Test
  public void putStatic_stringNotNull_ok() throws Exception {

    // test with a non-null String
    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";

    String originalValue = "I'm classy";
    String newValue = "New dummy str";

    try {
      // get field
      ReturnValue retValue = callGetStatic(CLASS_NAME, fieldName);

      // test returned (original) value
      Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof String);
      assertEquals(originalValue, rawObj);

      // set a new value
      callPutStatic(CLASS_NAME, fieldName, fieldClassName, newValue);

      // test that the field has now the new value
      retValue = callGetStatic(CLASS_NAME, fieldName);
      assertValueIsObjectOfType(retValue, fieldClassName);
      rawObj = Unwrapper.unwrapObject(retValue.getObject());
      assertTrue(rawObj instanceof String);
      assertEquals(newValue, rawObj);
    } finally {
      // now revert changed value to original (otherwise other tests may fail after a 1st run)
      callPutStatic(CLASS_NAME, fieldName, fieldClassName, originalValue);
    }
  }

  @Test
  public void putStatic_noSuchField_exThrown() {

    String fieldName = "aMadeUpField";
    String fieldClassName = "java.lang.String";

    callPutStatic(
        CLASS_NAME, fieldName, fieldClassName, "whatever", "java.lang.NoSuchFieldException");
  }

  @Test
  public void putStatic_noSuchClass_exThrown() {
    String nonExistingClass = "com.quasient.pal.apps.IDontExist";
    callGetStatic(nonExistingClass, "aStaticInteger", "java.lang.ClassNotFoundException");
  }

  @Test
  public void putField_integer_ok() throws Exception {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";

    Integer originalValue = 4;

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // get instance variable, assert original value
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, fieldName, newObjRef);
    Obj retObj = retValue.getObject();
    assertValueIsObjectOfType(retValue, fieldClassName);
    Object rawObj = Unwrapper.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    // now call set to modify
    Integer newValue = 500;
    callPutField(CLASS_NAME, fieldName, newObjRef, fieldClassName, newValue);

    // now get to test if set took place
    retValue = callGetInstanceVar(CLASS_NAME, fieldName, newObjRef);
    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(newValue, rawObj);
  }

  @Test
  public void putField_integerSetNull_ok() throws Exception {

    String fieldName = "anotherInt";
    String fieldClassName = "java.lang.Integer";

    Integer originalValue = 1;

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // test with a non-null integer
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, fieldName, newObjRef);
    Obj retObj = retValue.getObject();
    assertValueIsObjectOfType(retValue, fieldClassName);
    Object rawObj = Unwrapper.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    // set integer to null
    callPutField(CLASS_NAME, fieldName, newObjRef, fieldClassName, null);

    // now get to test if set took place
    retValue = callGetInstanceVar(CLASS_NAME, fieldName, newObjRef);
    assertValueIsNullObjectOfType(retValue, fieldClassName);
    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertNull(rawObj);
  }

  @Test
  public void putField_wrongType_exThrown() throws Exception {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());
    callPutField(
        CLASS_NAME,
        fieldName,
        newObjRef,
        fieldClassName,
        newValue,
        "java.lang.IllegalArgumentException");
  }

  @Test
  public void putField_noSuchField_exThrown() throws Exception {

    String fieldName = "aMadeUpField";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());
    callPutField(
        CLASS_NAME,
        fieldName,
        newObjRef,
        fieldClassName,
        newValue,
        "java.lang.NoSuchFieldException");
  }

  @Test
  public void putField_noSuchClass_exThrown() throws Exception {

    String nonExistingClass = "com.quasient.pal.apps.IDontExist";
    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());
    callPutField(
        nonExistingClass,
        fieldName,
        newObjRef,
        fieldClassName,
        newValue,
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void putField_noSuchInstance_npeThrown() {

    // fake non-existing instance
    ObjectRef badObjRef = ObjectRef.from("30482239");

    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    callPutField(
        CLASS_NAME,
        fieldName,
        badObjRef,
        fieldClassName,
        newValue,
        "java.lang.NullPointerException");
  }
}
