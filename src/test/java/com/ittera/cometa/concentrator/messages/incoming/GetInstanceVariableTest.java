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
 * - public integer with non-null value
 * - public integer with null value
 * - public string with non-null value
 * - public string with null value
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GetInstanceVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testGetIntegerNotNull() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!
//    App app = new App();

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 4;

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals(fieldClassName, retValue.getClazz().getName());

    Primitives.Object getObj = retValue.getObject();

    assertFalse(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertEquals(fieldClassName, getObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(getObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void testGetIntegerNull() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!

    String fieldName = "aNullInt";
    String fieldClassName = "java.lang.Integer";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a null (non-initialized) integer
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertEquals(fieldClassName, retValue.getClazz().getName());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertTrue(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertFalse(getObj.getClass_().getUnknown());
    assertEquals(fieldClassName, getObj.getClass_().getName());
  }

  @Test
  public void testGetStringNotNull() throws ClassNotFoundException {

    String fieldName = "someString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm blank";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null String (someString = "I'm blank")
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertEquals(fieldClassName, retValue.getClazz().getName());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertFalse(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertFalse(getObj.getClass_().getUnknown());
    assertEquals(fieldClassName, getObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(getObj);

    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);

  }

  @Test
  public void testGetStringNull() throws ClassNotFoundException {

    String fieldName = "aNullStr";
    String fieldClassName = "java.lang.String";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a null (non-initialized) string (aNullStr)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertEquals(fieldClassName, retValue.getClazz().getName());
    assertTrue(retValue.hasObject());

    Primitives.Object getObj = retValue.getObject();

    assertTrue(getObj.getIsNull());
    assertFalse(getObj.getIsArray());
    assertFalse(getObj.hasRef());
    assertTrue(getObj.hasClass_());
    assertEquals(fieldClassName, getObj.getClass_().getName());

  }

}
