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
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO arrays objectrefs rest of primitive types (?)
 */
public class GetInstanceVariableMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.rmi.explicit.InstanceVars";

  @Test
  public void getInstanceVariable_publicIntegerNotNull_intReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "anInt", newObjRef);

    assertValueIsObjectOfType(retValue, "java.lang.Integer");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(4, rawObj);
  }

  @Test
  public void getInstanceVariable_privateNullInteger_nullIntReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "aNullInt", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.Integer");
  }

  @Test
  public void getInstanceVariable_protectedStringNotNull_stringReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "someString", newObjRef);

    assertValueIsObjectOfType(retValue, "java.lang.String");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals("I'm not blank", rawObj);
  }

  @Test
  public void getInstanceVariable_getPublicStringNull_nullStringReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "aNullStr", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.String");
  }

  @Test
  public void getInstanceVariable_packageVisibleBooleanNull_nullBoolReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "aNullBool", newObjRef);

    assertValueIsNullObjectOfType(retValue, "java.lang.Boolean");
  }

  @Test
  public void getInstanceVariable_publicBoolNotNull_boolReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "aBool", newObjRef);

    assertValueIsObjectOfType(retValue, "boolean");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(true, rawObj);
  }

  @Test
  public void getInstanceVariable_privateShortNotZero_shortReturned() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    ReturnValue retValue = callGetInstanceVar(className, "someShort", newObjRef);

    assertValueIsObjectOfType(retValue, "short");
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short);
    assertEquals((short) 233, rawObj);
  }

  @Test
  public void getInstanceVariable_noSuchClass_exThrown() throws Exception {
    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    callGetInstanceVar(
        nonExistingClass, "someShort", newObjRef, "java.lang.ClassNotFoundException");
  }

  @Test
  public void getInstanceVariable_noSuchInstance_exThrown() throws Exception {
    // create new instance
    ObjectRef newObjRef = ObjectRef.from("38923");

    callGetInstanceVar(
        className, "someShort", newObjRef, "net.ittera.pal.common.objects.ObjectNotFoundException");
  }

  @Test
  public void getInstanceVariable_noSuchField_exThrown() throws Exception {

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now get instance variable
    callGetInstanceVar(className, "aMadeUpField", newObjRef, "java.lang.NoSuchFieldException");
  }
}
