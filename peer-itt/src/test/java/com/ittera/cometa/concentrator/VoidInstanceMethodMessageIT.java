package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 *
 * - package visible with no args
 * - private with 1 (primitive) arg
 * - protected no args
 *
 *
 * TODO
 * - arrays
 *
 */
public class VoidInstanceMethodMessageIT extends AbstractPeerIntegrationTest {

  protected final String className = "com.ittera.cometa.apps.App";

  @Test
  public void packageVisibleNoArgs() throws Exception {

    String methodName = "doSomething";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};

    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }

  @Test
  public void privateWithArg() throws Exception {

    String methodName = "testArg";

    String param = "testing testing 1 2 3";
    Object[] parameters = new Object[]{param};
    String[] parameterTypes = new String[]{param.getClass().getName()};

    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }

  @Test
  public void protectedNoArgs() throws Exception {

    String methodName = "printDate";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};

    //we need an App instance
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters, new String[parameters.length]);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }
}
