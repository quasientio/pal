package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.Primitives;

import com.ittera.cometa.distributor.messages.data.ProtobufUtils;
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
  public void testGetInteger() throws ClassNotFoundException {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!
//    App app = new App();

    String className = "com.ittera.cometa.demos.App";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "anInt", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(4, rawObj);

    //test with a null (non-initialized) integer
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "aNullInt", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    retObj = replyMsg.getReturnValue().getObject();
    assertTrue(retObj.getIsNull());

    //TODO: list all instance fields using reflection and call get for each
//     String fieldName = "someString";
//    String fieldName = "aBool";
//    String fieldName = "anApp";

  }

  @Test
  public void testGetString() throws ClassNotFoundException {

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
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);

    assertTrue(rawObj instanceof String);
    assertEquals("I'm blank", rawObj);

    //test with a null (non-initialized) string (aNullStr)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "aNullStr", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    retObj = replyMsg.getReturnValue().getObject();
    assertTrue(retObj.getIsNull());

  }

  @Test
  public void testPutInteger() throws ClassNotFoundException {

    String className = "com.ittera.cometa.demos.App";

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, "anInt", newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(4, rawObj);


    //set integer (value = 500)
    requestMsg = DataMessageFactory.buildPutObjectMessage(clientId, className, "anInt", newObj.getRef(), "java.lang.Integer", Integer.valueOf(500));
    replyMsg = sendAndReceive(requestMsg);
    logger.info("Received reply message:\n{}", replyMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
//    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

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
