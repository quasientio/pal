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

package net.ittera.pal.core;

import net.ittera.pal.common.lang.ObjectRef;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO - arrays
 */
public class VoidInstanceMethodMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.VoidInstanceMethods";

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_void() throws Exception {

    String methodName = "doSomething";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        new Object[parameterTypes.length],
        new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callInstanceMethod_privateWithArg_void() throws Exception {

    String methodName = "testArg";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String param = "testing testing 1 2 3";
    Object[] parameters = {param};
    String[] parameterTypes = {param.getClass().getName()};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callInstanceMethod_protectedNoArgs_void() throws Exception {

    String methodName = "printDate";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        new Object[parameterTypes.length],
        new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callInstanceMethod_nullArg_throwsEx() throws Exception {

    String methodName = "testNonNullArg";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String param = null;
    Object[] parameters = {param};
    String[] parameterTypes = {String.class.getName()};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.NullPointerException");
  }

  @Test
  public void callInstanceMethod_noSuchClass_throwsEx() throws Exception {
    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    String methodName = "testNonNullArg";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method on a wrong class
    String param = null;
    Object[] parameters = {param};
    String[] parameterTypes = {String.class.getName()};
    callVoidInstanceMethod(
        nonExistingClass,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void callInstanceMethod_noSuchMethod_throwsEx() throws Exception {

    String methodName = "a_made_up_method";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String param = null;
    Object[] parameters = {param};
    String[] parameterTypes = {String.class.getName()};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.NoSuchMethodException");
  }

  @Test
  public void callInstanceMethod_noSuchInstance_throwsEx() throws Exception {

    String methodName = "printDate";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from("2398248");

    // now call the method
    Object[] parameters = {};
    String[] parameterTypes = {};
    callVoidInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "net.ittera.pal.common.lang.ObjectNotFoundException");
  }
}
