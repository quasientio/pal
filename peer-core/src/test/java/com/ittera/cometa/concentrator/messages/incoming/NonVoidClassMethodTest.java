package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - private with arg, returns String
 * - protected with no args, returns Integer
 * - public with List as arg, returns Integer
 * - public returns null Object
 * - package returns char (primitive) array
 * - returns an empty Long array
 * - returns a null Boolean array
 * - returns an objectRef (of App)
 * - returns an array of objectRef's (of App'js)
 */

public class NonVoidClassMethodTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.apps.App";

  @Test
  public void privateWithArg() throws Exception {

    String methodName = "testNonVoidStatic";

    String param = "GIVE ME THIS IN LOWERCASE";
    Object[] parameters = new Object[]{param};
    String[] parameterTypes = new String[]{param.getClass().getName()};
    String shouldReturn = param.toLowerCase();

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, param.getClass().getName());

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void protectedNoArgs() throws Exception {

    String methodName = "highFive";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};
    Integer shouldReturn = 5;

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, shouldReturn.getClass().getName());

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void returnsIntegerSum() throws Exception {

    String methodName = "nonVoidSumUpList";

    //new ArrayList<Integer>
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, "java.util.ArrayList");
    DataMessage replyMsg = sendAndReceive(requestMsg);
    String listObjRef = replyMsg.getReturnValue().getObject().getRef();

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), "java.util.ArrayList");

    //add some int's
    int[] someInts = {39, 5, 58, 32, 70, 42};
    for (int i = 0; i < someInts.length; i++) {
      requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, "java.util.ArrayList", "add", listObjRef, new String[]{"java.lang.Integer"}, new Object[]{someInts[i]}, new String[someInts.length]);
      replyMsg = sendAndReceive(requestMsg);
    }

    //call method
    String[] parameterTypes = new String[]{"java.util.ArrayList"};
    String[] objRefs = new String[]{listObjRef};
    int sum = 0;
    for (int i = 0; i < someInts.length; i++) {
      sum += (Integer) someInts[i];
    }
    Integer shouldReturn = Integer.valueOf(sum);

    requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, new Object[parameterTypes.length], objRefs);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, shouldReturn.getClass().getName());


    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }


  @Test
  public void returningNullObject() throws Exception {

    String methodName = "giveMeANull";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};
    Object shouldReturn = null;

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, "java.lang.Object");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void returningCharArray() throws Exception {

    String methodName = "toCharArray";

    String param = "split me up";
    String[] parameterTypes = new String[]{param.getClass().getName()};
    Object[] parameters = new Object[]{param};
    char[] shouldReturn = param.toCharArray();

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsWrappedArrayOfRightType(retValue, shouldReturn.getClass().getName());

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (char[]) rawObj);
  }


  @Test
  public void returningEmptyArray() throws Exception {

    String methodName = "giveMeAnEmptyLongArray";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};
    Long[] shouldReturn = new Long[]{};

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsWrappedArrayOfRightType(retValue, shouldReturn.getClass().getName());

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (Long[]) rawObj);
  }

  @Test
  public void returningNullArray() throws Exception {

    String methodName = "giveMeANullBoolArray";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};
    Boolean[] shouldReturn = (Boolean[]) null;

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, "[Ljava.lang.Boolean;");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertArrayEquals(shouldReturn, (Boolean[]) rawObj);
  }

  @Test
  public void returningObjectRef() throws Exception {

    String methodName = "fetchMeAnApp";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectRefOfRightType(retValue, className);

    //with a 2nd call we should get the same App instance objectRef, let's make sure
    String appRef = retValue.getObject().getRef();

    requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertValueIsObjectRefOfRightType(retValue, className);

    String secondAppRef = retValue.getObject().getRef();
    assertEquals(appRef, secondAppRef);

    //OK, so we got an App instance objref, let's get some field value

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 4;

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, appRef);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void returningObjectRefArray() throws Exception {

    String methodName = "fetchMeAnAppArray";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};

    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, String.format("[L%s;", className));

    //TODO make sure each element is an objectRef, and compare values of two of them

  }

}

