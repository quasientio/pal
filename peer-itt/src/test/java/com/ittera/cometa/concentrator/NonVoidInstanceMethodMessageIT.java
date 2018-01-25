package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.List;
import java.util.ArrayList;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - package visible, no args, returns primitive wrapper (Integer)
 * - public, no args, returns List<String>
 * - protected with objects and objectRefs as args, returns Integer
 * <p>
 * TODO:
 * - arrays
 */
public class NonVoidInstanceMethodMessageIT extends AbstractPeerIntegrationTest {

  protected final String className = "com.ittera.cometa.apps.App";

  @Test
  public void packageVisibleNoArgs() throws Exception {
    String methodName = "giveMeX";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};
    Integer shouldReturn = 4;


    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }


  @Test
  public void publicReturnsListAsRef() throws Exception {
    String methodName = "getListOfStrings";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};
    List<String> shouldReturn = new ArrayList();

    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectRefOfType(retValue, "java.util.List");
    //assertValueIsObjectRefOfType(retValue, shouldReturn.getClass().getName()); <-- fails because it returns List<>, not ArrayList<>
    //TODO assert method in AbstractPeerIntegrationTest should check also for interfaces

    //TODO iterate through list and check values
//    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
//    assertEquals(shouldReturn, rawObj);
  }

  /**
   * Very similar to above test, but return value here is not a ref!
   */
  @Test
  public void publicReturnsNativelyInitListAsRef() throws Exception {
    String methodName = "getListOfStringsShorthand";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};
    List<String> shouldReturn = new ArrayList();

    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectRefOfType(retValue, "java.util.List");
    //assertValueIsObjectRefOfType(retValue, shouldReturn.getClass().getName()); <-- fails because it returns List<>, not ArrayList<>
    //TODO assert method in AbstractPeerIntegrationTest should check also for interfaces

    //TODO iterate through list and check values
//    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
//    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void withObjectsAndObjectrefsAsArgs() throws Exception {

    String methodName = "addOffsetToListAndSumUp";

    //new ArrayList<Integer>
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, "java.util.ArrayList");
    DataMessage replyMsg = sendAndReceive(requestMsg);
    String listObjRef = replyMsg.getReturnValue().getObject().getRef();

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfType(replyMsg.getReturnValue(), "java.util.ArrayList");

    //add some int's to the list
    int[] someInts = {1, 2, 3, 5, 7, 9};
    for (int i = 0; i < someInts.length; i++) {
      requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, "java.util.ArrayList", "add", listObjRef, new String[]{"java.lang.Integer"}, new Object[]{someInts[i]}, new String[1]);
      replyMsg = sendAndReceive(requestMsg);
    }

    //now create an App instance
    requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //prepare parameters, expected return value
    String[] parameterTypes = new String[]{"int", "java.util.ArrayList"};
    int offsetParam = 10;
    Object[] objs = new Object[]{offsetParam, null};
    String[] objRefs = new String[]{null, listObjRef};
    Integer shouldReturn = 0;
    for (int i = 0; i < someInts.length; i++) {
      shouldReturn += someInts[i] + offsetParam;
    }

    //call method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, myApp.getRef(), parameterTypes, objs, objRefs);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());

    //assert the value returned is correct
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);

  }
}
