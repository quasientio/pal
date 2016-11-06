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
 * - package int with non-null value
 * - public string with non-null value
 * <p>
 * TODO:
 * - null value
 * - private, protected, package-visible
 * - primitives
 * - arrays
 * - objectrefs
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SetClassVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testPutStaticIntegerNotNull() throws ClassNotFoundException {

    String fieldName = "aStaticInteger";
    String fieldClassName = "int";
    Integer originalValue = 3000;
    Integer newValue = 3200;

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Values.ReturnValue retValue = replyMsg.getReturnValue();

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);

    //set a new value
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, fieldClassName, newValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);


    //test that the field has now the new value
    requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(newValue, rawObj);

    //end of test

    //now revert changed value to original (otherwise other tests may fail after a 1st run)

    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, fieldClassName, originalValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);
  }

  @Test
  public void testPutStaticStringNotNull() throws ClassNotFoundException {

    //test with a non null String
    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm classy";
    String newValue = "New dummy str";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    Values.ReturnValue retValue = replyMsg.getReturnValue();

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);

    //set a new value
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, fieldClassName, newValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    Fields.StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);


    //test that the field has now the new value
    requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals(newValue, rawObj);

    //end of test

    //now revert changed value to original (otherwise other tests may fail after a 1st run)

    //set a new value
    requestMsg = DataMessageFactory.buildPutStaticMessage(clientId, className, fieldName, fieldClassName, originalValue);
    replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasStaticFieldPutDone());
    assertFalse(replyMsg.hasReturnValue());
    staticFieldPutDone = replyMsg.getStaticFieldPutDone();
    assertEquals(staticFieldPutDone.getField().getName(), fieldName);
  }

}
