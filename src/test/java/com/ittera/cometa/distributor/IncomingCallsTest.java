package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.Primitives;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Modifier;

import com.ittera.cometa.distributor.messages.data.DataMessageFactory;
import com.ittera.cometa.distributor.messages.data.Wrappers.DataMessage;

import static org.junit.Assert.*;

import java.io.IOException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IncomingCallsTest extends AbstractDistributorTest {

  public IncomingCallsTest() throws IOException {
    super();
  }

  @Test
  public void testConstructor() {
    String className = "com.ittera.cometa.demos.App";

    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    logger.info("Received reply message:\n{}", replyMsg);

    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    assertEquals(className, newObj.getClass_().getName());
    assertTrue(newObj.hasRef());
    logger.info("Got new objectRef: {}", newObj.getRef());
  }

  @Test
  public void testVoidClassMethod_Main() {

    //test main
    /** example: com.ittera.cometa.demos.App main */
    String className = "com.ittera.cometa.demos.App";
    String methodName = "main";

    int modifiers = Modifier.PUBLIC | Modifier.STATIC;
    Class returnType = Void.class;
    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[]{new String[]{}};

    DataMessage requestMsg = DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, modifiers, returnType, parameterTypesNamesArray, parameters);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }
}
