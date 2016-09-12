package com.ittera.cometa.distributor;

import org.junit.Test;

import java.lang.reflect.Modifier;

import com.ittera.cometa.distributor.messages.data.DataMessageFactory;

import static org.junit.Assert.*;

import java.io.IOException;

public class IncomingCallsTest extends AbstractDistributorTest {

  public IncomingCallsTest() throws IOException {
    super();
  }

  @Test
  public void testVoidClassMethod() {
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

    Long sentRecordOffset = send(DataMessageFactory.buildClassMethodMessage(clientId, className, methodName, modifiers, returnType, parameterTypesNamesArray, parameters));
    logger.info("Message was sent with offset: {}", sentRecordOffset);
  }
}
