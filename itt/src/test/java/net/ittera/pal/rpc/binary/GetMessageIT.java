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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.Unwrapper;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior.
 *
 * <p>TODO: arrays objectrefs rest of primitive types (?)
 */
public class GetMessageIT extends AbstractBinaryRPCMessageIT {

  private static final String CLASS_NAME = "net.ittera.pal.apps.rpc.Variables";

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
  public void getClassVariable_noSuchClass_exThrown() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    callGetStatic(nonExistingClass, "aProtectedBool", "java.lang.ClassNotFoundException");
  }

  @Test
  public void getClassVariable_noSuchField_exThrown() throws Exception {
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

    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    callGetInstanceVar(
        nonExistingClass, "someShort", newObjRef, "java.lang.ClassNotFoundException");
  }

  @Test
  public void getInstanceVariable_noSuchInstance_npeThrown() throws Exception {
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
