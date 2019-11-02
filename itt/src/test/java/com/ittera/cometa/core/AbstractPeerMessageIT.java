package com.ittera.cometa.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ittera.cometa.common.BiMapObjectService;
import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Fields.InstanceFieldPutDone;
import com.ittera.cometa.messages.protobuf.Fields.StaticFieldPutDone;
import com.ittera.cometa.messages.protobuf.Values.ReturnValue;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPeerMessageIT extends ExecMessageAssertions {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  protected static final String TEST_PROPERTIES_PATH = "/tests.properties";

  protected static final UUID clientId = UUID.randomUUID();

  protected static MessageBuilder messageBuilder;
  private static ThinPeer thinPeer;

  @BeforeClass
  public static void initialize() throws Exception {

    // configure wiring
    AbstractModule module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ObjectService.class).to(BiMapObjectService.class).asEagerSingleton();
            bind(MessageBuilder.class).to(ProtobufMessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    final Properties properties = new Properties();
    try (final InputStream stream =
        AbstractPeerMessageIT.class.getResourceAsStream(TEST_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    thinPeer = new ThinPeer(properties);
  }

  protected ExecMessage sendAndReceive(ExecMessage message) throws Exception {
    return thinPeer.sendAndReceive(message, true);
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  /** Helper Methods */
  protected ReturnValue callConstructor(
      String className, Class[] parameterTypes, Object[] args, ObjectRef[] argObjRefs)
      throws Exception {
    return callConstructor(className, parameterTypes, args, argObjRefs, null);
  }

  protected ReturnValue callConstructor(
      String className,
      Class[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs,
      String expectedThrowableType)
      throws Exception {
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    ExecMessage replyMsg =
        sendAndReceive(
            messageBuilder.buildNonEmptyConstructor(
                clientId, className, parameterTypesNamesArray, args, argObjRefs));

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
      assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
    }

    return replyMsg.getReturnValue();
  }

  protected ReturnValue callEmptyConstructor(String className) throws Exception {
    return callEmptyConstructor(className, null);
  }

  protected ReturnValue callEmptyConstructor(String className, String expectedThrowableType)
      throws Exception {
    ExecMessage replyMsg =
        sendAndReceive(messageBuilder.buildEmptyConstructor(clientId, className));

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
      assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
    }

    return replyMsg.getReturnValue();
  }

  protected ReturnValue callGetStatic(String className, String fieldName) throws Exception {
    return callGetStatic(className, fieldName, null);
  }

  protected ReturnValue callGetStatic(
      String className, String fieldName, String expectedThrowableType) throws Exception {
    ExecMessage requestMsg = messageBuilder.buildGetStatic(clientId, className, fieldName);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
    }

    return replyMsg.getReturnValue();
  }

  protected void callPutStatic(
      String className, String fieldName, String fieldClassName, Object value) throws Exception {
    callPutStatic(className, fieldName, fieldClassName, value, null);
  }

  protected void callPutStatic(
      String className,
      String fieldName,
      String fieldClassName,
      Object value,
      String expectedThrowableType)
      throws Exception {
    ExecMessage requestMsg =
        messageBuilder.buildPutStatic(clientId, className, fieldName, fieldClassName, value);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertFalse(replyMsg.hasReturnValue());
      assertTrue(replyMsg.hasStaticFieldPutDone());
      StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
      assertEquals(staticFieldPutDone.getField().getName(), fieldName);
    }
  }

  protected ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef)
      throws Exception {
    return callGetInstanceVar(className, fieldName, objRef, null);
  }

  protected ReturnValue callGetInstanceVar(
      String className, String fieldName, ObjectRef objRef, String expectedThrowableType)
      throws Exception {
    ExecMessage requestMsg = messageBuilder.buildGetObject(clientId, className, fieldName, objRef);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
    }

    return replyMsg.getReturnValue();
  }

  protected void callPutField(
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value)
      throws Exception {
    callPutField(className, fieldName, targetObjRef, valueClassName, value, null);
  }

  protected void callPutField(
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value,
      String expectedThrowableType)
      throws Exception {

    ExecMessage requestMsg =
        messageBuilder.buildPutObject(
            clientId, className, fieldName, targetObjRef, valueClassName, value);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertFalse(replyMsg.hasReturnValue());
      assertTrue(replyMsg.hasInstanceFieldPutDone());
      InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
      assertEquals(fieldPutDone.getField().getName(), fieldName);
    }
  }

  protected ReturnValue callClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    return callClassMethod(
        className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected ReturnValue callClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType)
      throws Exception {
    ExecMessage requestMsg =
        messageBuilder.buildClassMethod(
            clientId,
            className,
            methodName,
            parameterTypeNames,
            this,
            null,
            parameters,
            paramObjRefs);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
    }

    return replyMsg.getReturnValue();
  }

  protected void callVoidClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    callVoidClassMethod(className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected void callVoidClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType)
      throws Exception {
    ExecMessage requestMsg =
        messageBuilder.buildClassMethod(
            clientId,
            className,
            methodName,
            parameterTypeNames,
            this,
            null,
            parameters,
            paramObjRefs);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
      assertNotNull(replyMsg.getReturnValue());
      assertTrue(replyMsg.getReturnValue().getIsVoid());
    }
  }

  protected ReturnValue callInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    return callInstanceMethod(
        className, methodName, targetObjRef, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected ReturnValue callInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType)
      throws Exception {
    ExecMessage requestMsg =
        messageBuilder.buildInstanceMethod(
            clientId,
            className,
            methodName,
            null,
            targetObjRef,
            parameterTypeNames,
            parameters,
            paramObjRefs);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
    }

    return replyMsg.getReturnValue();
  }

  protected void callVoidInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    callVoidInstanceMethod(
        className, methodName, targetObjRef, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected void callVoidInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType)
      throws Exception {

    ExecMessage requestMsg =
        messageBuilder.buildInstanceMethod(
            clientId,
            className,
            methodName,
            null,
            targetObjRef,
            parameterTypeNames,
            parameters,
            paramObjRefs);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertTrue(replyMsg.hasReturnValue());
      assertNotNull(replyMsg.getReturnValue());
      assertTrue(replyMsg.getReturnValue().getIsVoid());
    }
  }
}
