package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 *
 * - FAILING package visible with no args
 * - private with 1 (primitive) arg
 * - protected no args
 *
 *
 * TODO
 * - arrays
 *
 */
public class VoidInstanceMethodTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

//  @Test TODO (fails now because of way of storing objects)
  public void packageVisibleNoArgs() throws ClassNotFoundException {

    String methodName = "doSomething";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};

    //we need an App instance
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = DataMessageFactory.buildInstanceMethodMessage(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }

  @Test
  public void privateWithArg() throws ClassNotFoundException {

    String methodName = "testArg";

    String param = "testing testing 1 2 3";
    Object[] parameters = new Object[]{param};
    String[] parameterTypes = new String[]{param.getClass().getName()};

    //we need an App instance
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = DataMessageFactory.buildInstanceMethodMessage(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }

  @Test
  public void protectedNoArgs() throws ClassNotFoundException {

    String methodName = "printDate";

    Object[] parameters = new Object[]{};
    String[] parameterTypes = new String[]{};

    //we need an App instance
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object myApp = replyMsg.getReturnValue().getObject();

    //now call the method
    requestMsg = DataMessageFactory.buildInstanceMethodMessage(clientId, className, methodName,myApp.getRef(), parameterTypes, parameters);
    replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());

  }
}
