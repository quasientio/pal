package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;


import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - public no args
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConstructorTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testConstructor() {

    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals(className, retValue.getClazz().getName());

    Primitives.Object newObj = retValue.getObject();

    assertFalse(newObj.getIsNull());
    assertFalse(newObj.getIsArray());
    assertTrue(newObj.hasRef());
    assertTrue(newObj.hasClass_());
    assertEquals(className, newObj.getClass_().getName());
    logger.info("Got new objectRef: {}", newObj.getRef());
  }

}
