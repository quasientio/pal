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

package net.ittera.pal.rpc.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.rpc.AbstractPeerMessageIT;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: - private, protected, package-visible - primitives - arrays - objectrefs
 */
public class SetInstanceVariableMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.rpc.InstanceVars";

  @Test
  public void putField_integer_ok() throws Exception {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";

    Integer originalValue = 4;
    Integer newValue = 500;

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // get instance variable, assert original value
    ReturnValue retValue = callGetInstanceVar(className, fieldName, newObjRef);
    Obj retObj = retValue.getObject();
    assertValueIsObjectOfType(retValue, fieldClassName);
    Object rawObj = Unwrapper.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    // now call set to modify
    callPutField(className, fieldName, newObjRef, fieldClassName, newValue);

    // now get to test if set took place
    retValue = callGetInstanceVar(className, fieldName, newObjRef);
    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(newValue, rawObj);
  }

  @Test
  public void putField_integerSetNull_ok() throws Exception {

    String fieldName = "anotherInt";
    String fieldClassName = "java.lang.Integer";

    Integer originalValue = 1;
    Integer newValue = null;

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // test with a non null integer
    ReturnValue retValue = callGetInstanceVar(className, fieldName, newObjRef);
    Obj retObj = retValue.getObject();
    assertValueIsObjectOfType(retValue, fieldClassName);
    Object rawObj = Unwrapper.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    // set integer to null
    callPutField(className, fieldName, newObjRef, fieldClassName, newValue);

    // now get to test if set took place
    retValue = callGetInstanceVar(className, fieldName, newObjRef);
    assertValueIsNullObjectOfType(retValue, fieldClassName);
    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(newValue, rawObj);
  }

  @Test
  public void putField_wrongType_exThrown() throws Exception {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());
    callPutField(className, fieldName, newObjRef, fieldClassName, newValue);
  }

  @Test
  public void putField_noSuchField_exThrown() throws Exception {

    String fieldName = "aMadeUpField";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());
    callPutField(
        className,
        fieldName,
        newObjRef,
        fieldClassName,
        newValue,
        "java.lang.NoSuchFieldException");
  }

  @Test
  public void putField_noSuchClass_exThrown() throws Exception {

    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());
    callPutField(
        nonExistingClass,
        fieldName,
        newObjRef,
        fieldClassName,
        newValue,
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void putField_noSuchInstance_npeThrown() throws Exception {

    // fake non-existing instance
    ObjectRef badObjRef = ObjectRef.from("30482239");

    String fieldName = "anInt";
    String fieldClassName = "java.lang.String";
    String newValue = "500";

    callPutField(
        className,
        fieldName,
        badObjRef,
        fieldClassName,
        newValue,
        "java.lang.NullPointerException");
  }
}
