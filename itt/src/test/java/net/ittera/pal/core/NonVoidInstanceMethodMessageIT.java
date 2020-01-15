package net.ittera.pal.core;

import static org.junit.Assert.assertEquals;

import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Values.ReturnValue;
import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>TODO: - arrays
 */
public class NonVoidInstanceMethodMessageIT extends AbstractPeerMessageIT {

  protected final String className = "net.ittera.pal.apps.NonVoidInstanceMethods";

  @Test
  public void callInstanceMethod_packageVisibleNoArgs_retValue() throws Exception {

    String methodName = "giveMeX";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    Integer shouldReturn = 4;
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_publicReturnsListAsRef_retValue() throws Exception {

    String methodName = "getListOfStrings";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_publicReturnsNativelyInitListAsRef_retValue() throws Exception {
    String methodName = "getListOfStringsShorthand";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // now call the method
    String[] parameterTypes = {};
    ReturnValue retValue =
        callInstanceMethod(
            className,
            methodName,
            newObjRef,
            parameterTypes,
            new Object[parameterTypes.length],
            new ObjectRef[parameterTypes.length]);

    // test returned value
    assertValueIsObjectRefOfType(retValue, "java.util.List");
  }

  @Test
  public void callInstanceMethod_withObjectsAndObjectrefsAsArgs_retValue() throws Exception {

    String methodName = "addOffsetToListAndSumUp";

    // new ArrayList<Integer>
    ObjectRef listObjRef =
        ObjectRef.from(callEmptyConstructor("java.util.ArrayList").getObject().getRef());

    // add some int's to the list
    int[] someInts = {1, 2, 3, 5, 7, 9};
    for (int someInt : someInts) {
      callInstanceMethod(
          "java.util.ArrayList",
          "add",
          listObjRef,
          new String[] {"java.lang.Integer"},
          new Object[] {someInt},
          new ObjectRef[someInts.length]);
    }

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

    // prepare parameters, expected return value
    String[] parameterTypes = {"int", "java.util.ArrayList"};
    int offsetParam = 10;
    Object[] parameters = {offsetParam, null};
    ObjectRef[] paramObjRefs = {null, listObjRef};

    // now call the method
    ReturnValue retValue =
        callInstanceMethod(
            className, methodName, newObjRef, parameterTypes, parameters, paramObjRefs);

    // test returned value
    Integer shouldReturn = 0;
    for (int someInt : someInts) {
      shouldReturn += someInt + offsetParam;
    }
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callInstanceMethod_throwsCheckedException_exThrown() throws Exception {

    String methodName = "throwMeACheckedException";

    // create new instance
    ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());
    Object param = new Long(Integer.MAX_VALUE) + 1;

    // now call the method
    String[] parameterTypes = {param.getClass().getTypeName()};
    Object[] parameters = {param};

    callInstanceMethod(
        className,
        methodName,
        newObjRef,
        parameterTypes,
        parameters,
        new ObjectRef[parameterTypes.length],
        "java.lang.Exception");
  }
}
