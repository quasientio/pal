package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.*;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.ittera.cometa.demos.App;


import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * - public no args
 * - public with args
 * - null as arg
 * - objectref as arg
 * - array as arg
 * <p>
 * TODO
 * - varargs
 * - invoke constructor using constructor-ref (requires [ticket:15])
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConstructorTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.App";

  @Test
  public void publicEmptyConstructor() {

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
  public void publicNonEmptyConstructor() {

    Object[] args = {"Constructing an app", Integer.valueOf(5)};
    String[] argRefs = {null, null};
    Class[] parameterTypes = new Class[]{String.class, Integer.class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    DataMessage requestMsg = DataMessageFactory.buildNonEmptyConstructorMessage(clientId, className, parameterTypesNamesArray, args, argRefs);
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
  public void publicNonEmptyConstructorNullArg() {

    Object[] args = {null};
    String[] argRefs = {null};
    Class[] parameterTypes = new Class[]{Integer.class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    DataMessage requestMsg = DataMessageFactory.buildNonEmptyConstructorMessage(clientId, className, parameterTypesNamesArray, args, argRefs);
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
  public void publicNonEmptyConstructorArrayArg() {

    Object[] args = {new String[]{"Aa", "Bb", "Cc"}};
    String[] argRefs = {null};
    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    DataMessage requestMsg = DataMessageFactory.buildNonEmptyConstructorMessage(clientId, className, parameterTypesNamesArray, args, argRefs);
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
  public void publicConstructorObjectrefArg() {

    //1. Construct an App calling no-args constructor
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

    String newAppRef = newObj.getRef();

    //2. Construct an App calling the constructor that takes another App as arg

    Object[] args = {null};
    String[] argRefs = {newAppRef};
    Class[] parameterTypes = new Class[]{App.class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    requestMsg = DataMessageFactory.buildNonEmptyConstructorMessage(clientId, className, parameterTypesNamesArray, args, argRefs);
    replyMsg = sendAndReceive(requestMsg);

    logger.info("Received reply message:\n{}", replyMsg);
    assertTrue(replyMsg.hasReturnValue());
    retValue = replyMsg.getReturnValue();
    assertFalse(retValue.getIsVoid());
    assertFalse(retValue.getIsClass());
    assertTrue(retValue.hasClazz());
    assertTrue(retValue.hasObject());
    assertEquals(className, retValue.getClazz().getName());

    newObj = retValue.getObject();

    assertFalse(newObj.getIsNull());
    assertFalse(newObj.getIsArray());
    assertTrue(newObj.hasRef());
    assertTrue(newObj.hasClass_());
    assertEquals(className, newObj.getClass_().getName());
    logger.info("Got new objectRef: {}", newObj.getRef());

  }

}
