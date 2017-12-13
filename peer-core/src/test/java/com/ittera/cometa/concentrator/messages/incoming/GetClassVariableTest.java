package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * This class should only test access to static (i.e. class) variables.
 * - public String with non-null value
 * - public String with null value
 * - private Integer with non-null value
 * - protected Boolean with null value
 * - package-visible boolean with non-null value
 * <p>
 * Regardless of their type and visibility.
 * <p>
 * TODO
 * arrays
 * objectrefs
 * rest of primitive types (?)
 */
public class GetClassVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.apps.App";

  @Test
  public void getStaticStringPublicNotNull() throws Exception {

    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm classy";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getStaticStringPublicNull() throws Exception {

    String fieldName = "aNullStaticStr";
    String fieldClassName = "java.lang.String";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticIntegerPrivateNotNull() throws Exception {

    String fieldName = "aPrivateClassInt";
    String fieldClassName = "java.lang.Integer";
    Integer originalValue = 39328;

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void getStaticBooleanProtectedNull() throws Exception {

    String fieldName = "aProtectedBool";
    String fieldClassName = "java.lang.Boolean";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticBooleanPackageVisibleNotNull() throws Exception {

    String fieldName = "aPackageVisibleBool";
    String fieldClassName = "boolean";
    boolean originalValue = true;

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(originalValue, rawObj);
  }


}
