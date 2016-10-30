package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Modifier;

import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VoidClassMethodTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testMain() {

    //test main
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

    assertNotNull(replyMsg.getReturnValue());
    assertTrue(replyMsg.getReturnValue().getIsVoid());
  }
}
