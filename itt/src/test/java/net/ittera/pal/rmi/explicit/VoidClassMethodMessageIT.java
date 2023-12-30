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

import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.rmi.AbstractPeerMessageIT;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO we should check the calls worked: As these methods are void, we should store some value
 * in a field of the target object and check it (+ revert it)
 */
public class VoidClassMethodMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.rmi.explicit.VoidStaticMethods";

  @Test
  public void callClassMethod_privateWithArg_void() throws Exception {

    String methodName = "testVoidStatic";

    String[] parameterTypes = {"java.lang.String"};
    Object[] parameters = {"Hello from a unit test"};

    // test call
    callVoidClassMethod(
        className, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_privateWithPrimitiveAndWrapperArgs_void() throws Exception {

    String methodName = "printArg";

    String[] parameterTypes = {"int", "java.lang.String"};
    Object[] parameters = {2, "more than an argument"};

    // test call
    callVoidClassMethod(
        className, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_packageWithNoArgs_void() throws Exception {

    String methodName = "doSomethingStatically";

    String[] parameterTypes = {};
    Object[] parameters = {};

    // test call
    callVoidClassMethod(
        className, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_publicStaticVoidMain_void() throws Exception {

    // test main
    String methodName = "main";

    String[] parameterTypes = {"[Ljava.lang.String;"};
    Object[] parameters = {new String[] {}};

    // test call
    callVoidClassMethod(
        className, methodName, parameterTypes, parameters, new ObjectRef[parameterTypes.length]);
  }

  @Test
  public void callClassMethod_withObjectrefAsArg_void() throws Exception {

    String methodName = "sumUpList";

    // new ArrayList<Integer>
    ObjectRef listObjRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add some int's
    int[] someInts = {39, 5, 58, 32, 70, 42};
    for (int someInt : someInts) {
      callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          new String[] {"java.lang.Integer"},
          new Object[] {someInt},
          new ObjectRef[] {null});
    }

    String[] parameterTypes = {"java.util.ArrayList"};
    Object[] parameters = new Object[parameterTypes.length];
    ObjectRef[] paramObjRefs = {listObjRef};

    // test call
    callVoidClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);
  }

  @Test
  public void callClassMethod_noSuchClass_exThrown() throws Exception {

    String nonExistingClass = "net.ittera.pal.apps.IDontExist";
    String methodName = "doSomethingStatically";

    String[] parameterTypes = {};
    Object[] parameters = {};

    // test call
    callVoidClassMethod(
        nonExistingClass,
        methodName,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.ClassNotFoundException");
  }

  @Test
  public void callClassMethod_noSuchMethod_exThrown() throws Exception {

    String methodName = "a_made_up_method";

    String[] parameterTypes = {};
    Object[] parameters = {};

    // test call
    callVoidClassMethod(
        className,
        methodName,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.NoSuchMethodException");
  }

  @Test
  public void callClassMethod_throwsRuntimeEx_exThrown() throws Exception {

    String methodName = "throwRuntimeException";

    String[] parameterTypes = {};
    Object[] parameters = {};

    // test call
    callVoidClassMethod(
        className,
        methodName,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.RuntimeException");
  }
}
