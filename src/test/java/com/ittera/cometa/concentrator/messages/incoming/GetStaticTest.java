package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GetStaticTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testGetStaticString_notNull() throws ClassNotFoundException {

    //test with a non null String
    String originalStrValue = "I'm classy";
    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, "aClassString");
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals("java.lang.String", retValue.getClazz().getName());

    Primitives.Object retObj = retValue.getObject();
    assertFalse(retObj.getIsArray());
    assertFalse(retObj.getIsNull());
    assertFalse(retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertFalse(retObj.getClass_().getUnknown());
    assertEquals("java.lang.String", retObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof String);
    assertEquals(originalStrValue, rawObj);
  }

  @Test
  public void testGetStaticString_Null() throws ClassNotFoundException {

    //test with a null String
    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, "aNullStaticStr");
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertFalse(retValue.hasClazz());
    assertNotNull(retValue.getObject());

    Primitives.Object retObj = retValue.getObject();
    assertFalse(retObj.getIsArray());
    assertTrue(retObj.getIsNull());
    assertFalse(retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertTrue(retObj.getClass_().getUnknown());

  }

}
