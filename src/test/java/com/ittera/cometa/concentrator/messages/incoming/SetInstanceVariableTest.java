package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.protobuf.Unwrapper;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields;
import com.ittera.cometa.concentrator.messages.protobuf.data.Primitives;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - public integer with non-null value
 * - public integer with non-null value set null
 * - null value
 * <p>
 * TODO:
 * - private, protected, package-visible
 * - primitives
 * - arrays
 * - objectrefs
 */
public class SetInstanceVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testPutInteger() throws ClassNotFoundException {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 4;
    Integer newValue = 500;

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer
    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = Unwrapper.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    //set integer
    requestMsg = dataMessageBuilder.buildPutObject(clientId, className, fieldName, newObj.getRef(), fieldClassName, newValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasInstanceFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
    assertEquals(fieldPutDone.getField().getName(), fieldName);


    //now get to test if set took place
    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(newValue, rawObj);
  }

  @Test
  public void testPutIntegerSetNull() throws ClassNotFoundException {

    String fieldName = "anotherInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 1;
    Integer newValue = null;

    //must call new first
    DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object fieldObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer
    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, fieldObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = Unwrapper.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    //set integer to null
    requestMsg = dataMessageBuilder.buildPutObject(clientId, className, fieldName, fieldObj.getRef(), fieldClassName, newValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasInstanceFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
    assertEquals(fieldPutDone.getField().getName(), fieldName);


    //now get to test if set took place
    requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, fieldObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);

    rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertEquals(newValue, rawObj);
  }
}
