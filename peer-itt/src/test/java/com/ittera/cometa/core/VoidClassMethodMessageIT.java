package com.ittera.cometa.core;

import com.ittera.cometa.common.lang.ObjectRef;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO we should check the calls worked: As these methods are void, we should store some value
 * in a field of the target object and check it (+ revert it)
 */
public class VoidClassMethodMessageIT extends AbstractPeerMessageIT {

  protected final String className = "com.ittera.cometa.apps.VoidStaticMethods";

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
          new ObjectRef[someInts.length]);
    }

    String[] parameterTypes = {"java.util.ArrayList"};
    Object[] parameters = new Object[parameterTypes.length];
    ObjectRef[] paramObjRefs = {listObjRef};

    // test call
    callVoidClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);
  }

  @Test
  public void callClassMethod_noSuchClass_exThrown() throws Exception {

    String nonExistingClass = "com.ittera.cometa.apps.IDontExist";
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
