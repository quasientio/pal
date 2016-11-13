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
 * - private with array as arg
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

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
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

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
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

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
  }

  @Test
  public void privateNonEmptyConstructorArrayArg() {

    Object[] args = {new String[]{"Aa", "Bb", "Cc"}};
    String[] argRefs = {null};
    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    DataMessage requestMsg = DataMessageFactory.buildNonEmptyConstructorMessage(clientId, className, parameterTypesNamesArray, args, argRefs);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
  }

  @Test
  public void publicConstructorObjectrefArg() {

    //1. Construct an App calling no-args constructor
    DataMessage requestMsg = DataMessageFactory.buildEmptyConstructorMessage(clientId, className);
    DataMessage replyMsg = sendAndReceive(requestMsg);

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    String newAppRef = retValue.getObject().getRef();

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

    assertTrue(replyMsg.hasReturnValue());
    assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
  }

}
