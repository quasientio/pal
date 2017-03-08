package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - private with 1 (primitive) arg
 * - private with 1 primitive and 1 wrapper arg
 * - package visible with no args
 * - public with args (psvm, i.e. main)
 * - with objectRef as arg
 * TODO we should check the calls worked: As these methods are void, we should store some value in a field of the target object and check it (+ revert it)
 */
public class VoidClassMethodTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void privateWithArg() {

    String methodName = "testVoidStatic";

    String[] parameterTypes = new String[]{"java.lang.String"};
    Object[] parameters = new Object[]{"Hello from a unit test"};

    DataMessage requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }

  @Test
  public void privateWithPrimitiveAndWrapperArgs() {

    String methodName = "printArg";

    String[] parameterTypes = new String[]{"int", "java.lang.String"};
    Object[] parameters = new Object[]{2, "more than an argument"};

    DataMessage requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }

  @Test
  public void packageWithNoArgs() {

    String methodName = "doSomethingStatically";

    String[] parameterTypes = new String[]{};
    Object[] parameters = new Object[]{};

    DataMessage requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }


  @Test
  public void publicStaticVoidMain() {

    //test main
    String methodName = "main";

    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[]{new String[]{}};

    DataMessage requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }

  @Test
  public void withObjectrefAsArg() {

    String methodName = "sumUpList";

    //new ArrayList<Integer>
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, "java.util.ArrayList");
    DataMessage replyMsg = sendAndReceive(requestMsg);
    String listObjRef = replyMsg.getReturnValue().getObject().getRef();

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), "java.util.ArrayList");

    //add some int's
    int[] someInts = {39,5,58,32,70, 42};
    for (int i = 0; i < someInts.length; i++) {
      requestMsg = DataMessageFactory.buildInstanceMethodMessage(clientId, "java.util.ArrayList", "add", listObjRef, new String[]{"java.lang.Integer"}, new Object[]{someInts[i]});
      replyMsg = sendAndReceive(requestMsg);
      //TODO we should check if this call worked, unless we do a check at the end of the static method call (see TODO in this class' javadoc)
    }

    //call method
    String[] parameterTypes = new String[]{"java.util.ArrayList"};
    String[] objRefs = new String[]{listObjRef};

    requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, parameterTypes, new Object[parameterTypes.length], objRefs);
    replyMsg = sendAndReceive(requestMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }
}
