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
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class GetMessageIT extends AbstractColferRpcMessageIT {

  private static final String CLASS_NAME = "com.quasient.pal.apps.rpc.Variables";

  public GetMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void getClassVariable_publicStringNotNull_varReturned() throws Exception {

    ReturnValue retValue = callGetStatic(CLASS_NAME, "aClassString");
    assertValueIsObjectOfType(retValue, "java.lang.String");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals("I'm classy", rawObj);
  }

  @Test
  public void getClassVariable_publicStringNull_nullStringReturned() throws Exception {

    ReturnValue retValue = callGetStatic(CLASS_NAME, "aNullStaticStr");
    assertValueIsNullObjectOfType(retValue, "java.lang.String");
  }

  @Test
  public void getClassVariable_privateIntegerNotNull_intReturned() throws Exception {

    ReturnValue retValue = callGetStatic(CLASS_NAME, "aPrivateClassInt");
    assertValueIsObjectOfType(retValue, "java.lang.Integer");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(39328, rawObj);
  }

  @Test
  public void getClassVariable_protectedBoolNull_nullBoolReturned() throws Exception {

    ReturnValue retValue = callGetStatic(CLASS_NAME, "aProtectedBool");
    assertValueIsNullObjectOfType(retValue, "java.lang.Boolean");
  }

  @Test
  public void getClassVariable_packageVisibleBoolNotNull_boolReturned() throws Exception {

    ReturnValue retValue = callGetStatic(CLASS_NAME, "aPackageVisibleBool");
    assertValueIsObjectOfType(retValue, "boolean");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(true, rawObj);
  }

  @Test
  public void getClassVariable_noSuchClass_exThrown() {
    String nonExistingClass = "com.quasient.pal.apps.IDontExist";
    callGetStatic(nonExistingClass, "aProtectedBool", "java.lang.ClassNotFoundException");
  }

  @Test
  public void getClassVariable_noSuchField_exThrown() {
    callGetStatic(CLASS_NAME, "aMadeUpField", "java.lang.NoSuchFieldException");
  }

  @Test
  public void getInstanceVariable_publicIntegerNotNull_intReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "anInt", newObjRef);

    assertValueIsObjectOfType(retValue, "java.lang.Integer");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(4, rawObj);
  }

  @Test
  public void getInstanceVariable_privateNullInteger_nullIntReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "myNullInt", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.Integer");
  }

  @Test
  public void getInstanceVariable_protectedStringNotNull_stringReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "someString", newObjRef);

    assertValueIsObjectOfType(retValue, "java.lang.String");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals("I'm not blank", rawObj);
  }

  @Test
  public void getInstanceVariable_getPublicStringNull_nullStringReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "myNullStr", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.String");
  }

  @Test
  public void getInstanceVariable_packageVisibleBooleanNull_nullBoolReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "myNullBool", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.Boolean");
  }

  @Test
  public void getInstanceVariable_publicBoolNotNull_boolReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "myBool", newObjRef);

    assertValueIsObjectOfType(retValue, "boolean");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(true, rawObj);
  }

  @Test
  public void getInstanceVariable_privateShortNotZero_shortReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(CLASS_NAME, "someShort", newObjRef);

    assertValueIsObjectOfType(retValue, "short");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short);
    assertEquals((short) 233, rawObj);
  }

  @Test
  public void getInstanceVariable_noSuchClass_exThrown() throws Exception {
    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    String nonExistingClass = "com.quasient.pal.apps.IDontExist";
    callGetInstanceVar(
        nonExistingClass, "someShort", newObjRef, "java.lang.ClassNotFoundException");
  }

  @Test
  public void getInstanceVariable_noSuchInstance_npeThrown() {
    // fake non-existing instance
    ObjectRef newObjRef = ObjectRef.from("38923");

    callGetInstanceVar(CLASS_NAME, "someShort", newObjRef, "java.lang.NullPointerException");
  }

  @Test
  public void getInstanceVariable_noSuchField_exThrown() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(CLASS_NAME).getObject().getRef());

    // now get instance variable
    callGetInstanceVar(CLASS_NAME, "aMadeUpField", newObjRef, "java.lang.NoSuchFieldException");
  }
}
