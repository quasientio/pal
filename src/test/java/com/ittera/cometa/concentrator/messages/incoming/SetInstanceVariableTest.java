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
 * <p>
 * TODO:
 * - null value
 * - private, protected, package-visible
 * - primitives
 * - arrays
 * - objectrefs
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SetInstanceVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testPutInteger() throws ClassNotFoundException {

    String fieldName = "anInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 4;
    Integer newValue = 500;

    //must call new first
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Primitives.Object newObj = replyMsg.getReturnValue().getObject();

    //test with a non null integer (value = 4)
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertNotNull(replyMsg.getReturnValue().getObject());
    Primitives.Object retObj = replyMsg.getReturnValue().getObject();

    Object rawObj = ProtobufUtils.unwrapObject(retObj);

    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    //set integer (value = 500)
    requestMsg = DataMessageFactory.buildPutObjectMessage(clientId, className, fieldName, newObj.getRef(), fieldClassName, newValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasInstanceFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
    assertEquals(fieldPutDone.getField().getName(), fieldName);


    //now get to test if set took place
    requestMsg = DataMessageFactory.buildGetObjectMessage(clientId, className, fieldName, newObj.getRef());
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(newValue, rawObj);
  }

}
