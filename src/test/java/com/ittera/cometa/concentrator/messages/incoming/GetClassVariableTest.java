package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.protobuf.DataMessageFactory;
import com.ittera.cometa.concentrator.messages.protobuf.Unwrapper;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

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
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
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
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
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
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
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
    assertValueIsNullObjectOfRightType(retValue, fieldClassName);
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
    assertValueIsObjectOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean);
    assertEquals(originalValue, rawObj);
  }


}
