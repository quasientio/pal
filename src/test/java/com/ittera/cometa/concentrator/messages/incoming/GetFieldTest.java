package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GetFieldTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testGetInteger_notNull() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!
//    App app = new App();


    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    Integer originalValue = 4;
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "anInt", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals("java.lang.Integer", retValue.getClazz().getName());

    Primitives.Object getObj = retValue.getObject();

    assertFalse(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertEquals("java.lang.Integer", getObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(getObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void testGetInteger_Null() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a null (non-initialized) integer
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "aNullInt", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertFalse(retValue.hasClazz());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertTrue(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertTrue(getObj.getClass_().getUnknown());
  }

  @Test
  public void testGetString_notNull() throws ClassNotFoundException {

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null String (someString = "I'm blank")
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "someString", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertEquals("java.lang.String", retValue.getClazz().getName());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertFalse(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertFalse(getObj.getClass_().getUnknown());
    assertEquals("java.lang.String", getObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(getObj);

    assertTrue(rawObj instanceof String);
    assertEquals("I'm blank", rawObj);

  }

  @Test
  public void testGetString_Null() throws ClassNotFoundException {

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a null (non-initialized) string (aNullStr)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "aNullStr", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertFalse(retValue.hasClazz());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertTrue(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertTrue(getObj.getClass_().getUnknown());

  }

}
