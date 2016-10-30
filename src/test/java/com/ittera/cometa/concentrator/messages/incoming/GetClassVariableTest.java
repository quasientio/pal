package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GetClassVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void testGetStaticStringNotNull() throws ClassNotFoundException {

    String fieldName = "aClassString";
    String fieldClassName = "java.lang.String";
    String originalValue = "I'm classy";

    //test with a non null String
    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals(fieldClassName, retValue.getClazz().getName());

    Primitives.Object retObj = retValue.getObject();
    assertFalse(retObj.getIsArray());
    assertFalse(retObj.getIsNull());
    assertFalse(retObj.hasRef());
    assertTrue(retObj.hasClass_());
    assertFalse(retObj.getClass_().getUnknown());
    assertEquals(fieldClassName, retObj.getClass_().getName());

    Object rawObj = ProtobufUtils.unwrapObject(retObj);
    assertTrue(rawObj instanceof String);
    assertEquals(originalValue, rawObj);
  }

  @Test
  public void testGetStaticStringNull() throws ClassNotFoundException {

    String fieldName = "aNullStaticStr";
    String fieldClassName = "java.lang.String";

    //test with a null String
    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
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

}
