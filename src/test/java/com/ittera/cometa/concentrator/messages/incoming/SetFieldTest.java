package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SetFieldTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testPutInteger() throws ClassNotFoundException {

    String fieldName = "anInt";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    Integer originalValue = 4;
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);


    //set integer (value = 500)
    Integer newIntValue = 500;
    requestMsg = DataMessageFactory.buildPutObjectMessage(clientId, className, fieldName, newObj.getRef(), "java.lang.Integer", newIntValue);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasInstanceFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
    assertEquals(fieldPutDone.getField().getName(), fieldName);


    //now get to test if set took place
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertEquals("java.lang.Integer", retValue.getClazz().getName());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertFalse(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertFalse(getObj.getClass_().getUnknown());
    assertEquals("java.lang.Integer", getObj.getClass_().getName());

    rawObj = ProtobufUtils.unwrapObject(getObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(newIntValue, rawObj);
  }

}
