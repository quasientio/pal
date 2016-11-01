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
 * - public static string with non-null value
 * - public static string with null value
 * - private static Integer with non-null value
 * - protected static Boolean with null value
 * - package-visible boolean (primitive) with non-null value (=true)
 * <p>
 * TODO:
 * Arrays
 * objectrefs
 * rest of primitive types (?)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GetClassVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void getStaticStringPublicNotNull() throws ClassNotFoundException {

    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm classy";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    valueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getStaticStringPublicNull() throws ClassNotFoundException {

    String fieldName = "aNullStaticStr";
    String fieldClassName = "java.lang.String";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    valueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticIntegerPrivateNotNull() throws ClassNotFoundException {

    String fieldName = "aPrivateClassInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 39328;

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    valueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getStaticBooleanProtectedNull() throws ClassNotFoundException {

    String fieldName = "aProtectedBool";
    String fieldClassName = "java.lang.Boolean";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    valueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticBooleanPackageVisibleNotNull() throws ClassNotFoundException {

    String fieldName = "aPackageVisibleBool";
    String fieldClassName = "boolean";
    boolean originalValue = true;

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    valueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(originalValue, rawObj);
  }

}
