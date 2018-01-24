package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * This class should only test access to instance variables.
 * <p>
 * Regardless of their type and visibility.
 * <p>
 * - public Integer with non-null value
 * - private Integer with null value
 * - protected String with non-null value
 * - public String with null value
 * - package-visible Boolean with null value
 * - public boolean with non-null value
 * - private short (primitive) non-zero
 * <p>
 * TODO
 * arrays
 * objectrefs
 * rest of primitive types (?)
 */
public class GetInstanceVariableMessageIT extends AbstractPeerIntegrationTest {

  protected final String className = "com.ittera.cometa.apps.App";

  @Test
  public void getIntegerPublicNotNull() throws Exception {
    //TODO have a native instance at hand for comparisons: the problem is that we need it in another path (not weaved) or loaded by another classloader!!
//    App app = new App();

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 4;

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getIntegerPrivateNull() throws Exception {

    String fieldName = "aNullInt";
    String fieldClassName = "java.lang.Integer";

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStringProtectedNotNull() throws Exception {

    String fieldName = "someString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm blank";

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);

  }

  @Test
  public void getStringPublicNull() throws Exception {

    String fieldName = "aNullStr";
    String fieldClassName = "java.lang.String";

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getBooleanPackageVisibleNull() throws Exception {

    String fieldName = "aNullBool";
    String fieldClassName = "java.lang.Boolean";

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getBooleanPublicNotNull() throws Exception {

    String fieldName = "aBool";
    String fieldClassName = "boolean";
    boolean originalValue = true;

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getShortPrivateNotZero() throws Exception {

    String fieldName = "someShort";
    String fieldClassName = "short";
    short originalValue = 233;

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short);
    assertEquals(originalValue, rawObj);
  }

}
