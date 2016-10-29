package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.data.*;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Modifier;

import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IncomingCallsTest extends AbstractConcentratorTest {

  @Test
  public void testConstructor() {
    String className = "com.ittera.cometa.demos.App";

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

  @Test
  public void testGetInteger_notNull() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!
//    App app = new App();

    String className = "com.ittera.cometa.demos.App";

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
//    App app = new App();

    String className = "com.ittera.cometa.demos.App";

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

    String className = "com.ittera.cometa.demos.App";

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

    String className = "com.ittera.cometa.demos.App";

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

  @Test
  public void testPutInteger() throws ClassNotFoundException {

    String className = "com.ittera.cometa.demos.App";
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

  @Test
  public void testGetStaticString_notNull() throws ClassNotFoundException {

    String className = "com.ittera.cometa.demos.App";

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

    String className = "com.ittera.cometa.demos.App";

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

  @Test
  public void testPutStaticInteger_notNull() throws ClassNotFoundException {

    String className = "com.ittera.cometa.demos.App";
    String fieldName = "aStaticInteger";

    Integer originalValue = 3000;

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    Primitives.Object retObj = retValue.getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    //set a new value
    Integer newIntValue = 3200;
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, "java.lang.Integer", newIntValue);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);


    //test that the field has now the new value
    requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals("java.lang.Integer", retValue.getClazz().getName());

    retObj = retValue.getObject();
    assertFalse(retObj.getIsArray());
    assertFalse(retObj.getIsNull());
    assertFalse(retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertFalse(retObj.getClass_().getUnknown());
    assertEquals("java.lang.Integer", retObj.getClass_().getName());

    rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof Integer);
    assertEquals(newIntValue, rawObj);

    //end of test

    //now revert changed value to original (otherwise other tests may fail after a 1st run)

    //set a new value
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, "java.lang.Integer", originalValue);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);
  }

  @Test
  public void testPutStaticString_notNull() throws ClassNotFoundException {

    String className = "com.ittera.cometa.demos.App";

    //test with a non null String
    String fieldName = "aClassString";
    String originalStrValue = "I'm classy";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    Primitives.Object retObj = retValue.getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof String);
    assertEquals(originalStrValue, rawObj);

    //set a new value
    String newStrValue = "New dummy str";
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, "java.lang.String", newStrValue);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);


    //test that the field has now the new value
    requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals("java.lang.String", retValue.getClazz().getName());

    retObj = retValue.getObject();
    assertFalse(retObj.getIsArray());
    assertFalse(retObj.getIsNull());
    assertFalse(retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertFalse(retObj.getClass_().getUnknown());
    assertEquals("java.lang.String", retObj.getClass_().getName());

    rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof String);
    assertEquals(newStrValue, rawObj);

    //end of test

    //now revert changed value to original (otherwise other tests may fail after a 1st run)

    //set a new value
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, "java.lang.String", originalStrValue);
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);
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
