package com.ittera.cometa.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ittera.cometa.common.ConcurrentHashMapObjectStore;
import com.ittera.cometa.common.ObjectStore;
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

  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

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
            bind(ObjectStore.class).to(ConcurrentHashMapObjectStore.class).asEagerSingleton();
            bind(MessageBuilder.class).to(ProtobufMessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    final Properties consumerProperties = new Properties();
    try (final InputStream stream =
        AbstractPeerMessageIT.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      consumerProperties.load(stream);
    }
    final Properties producerProperties = new Properties();
    try (final InputStream stream =
        AbstractPeerMessageIT.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      producerProperties.load(stream);
    }
    final String palDirectoryURL = System.getenv("PAL_DIRECTORY");
    if (palDirectoryURL == null) {
      throw new RuntimeException(
          "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2181)");
    }
    thinPeer =
        new ThinPeer()
            .withUUID(clientId)
            .withDirectoryURL(palDirectoryURL)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .init();
  }

  private ExecMessage sendAndReceive(ExecMessage message) throws Exception {
    ExecMessage reply;
    try {
      reply = thinPeer.sendAndReceive(message, true);
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving message with uuid: {}", message.getMessageUuid(), e);
      throw e;
    }
    return reply;
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  /** Helper Methods */
  ReturnValue callConstructor(
      String className, Class[] parameterTypes, Object[] args, ObjectRef[] argObjRefs)
      throws Exception {
    return callConstructor(className, parameterTypes, args, argObjRefs, null);
  }

  ReturnValue callConstructor(
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

  ReturnValue callEmptyConstructor(String className) throws Exception {
    return callEmptyConstructor(className, null);
  }

  private ReturnValue callEmptyConstructor(String className, String expectedThrowableType)
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

  ReturnValue callGetStatic(String className, String fieldName) throws Exception {
    return callGetStatic(className, fieldName, null);
  }

  ReturnValue callGetStatic(String className, String fieldName, String expectedThrowableType)
      throws Exception {
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

  void callPutStatic(String className, String fieldName, String fieldClassName, Object value)
      throws Exception {
    callPutStatic(className, fieldName, fieldClassName, value, null);
  }

  void callPutStatic(
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

  ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef)
      throws Exception {
    return callGetInstanceVar(className, fieldName, objRef, null);
  }

  ReturnValue callGetInstanceVar(
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

  void callPutField(
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value)
      throws Exception {
    callPutField(className, fieldName, targetObjRef, valueClassName, value, null);
  }

  void callPutField(
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

  ReturnValue callClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    return callClassMethod(
        className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  ReturnValue callClassMethod(
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

  void callVoidClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs)
      throws Exception {
    callVoidClassMethod(className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  void callVoidClassMethod(
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

  ReturnValue callInstanceMethod(
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

  ReturnValue callInstanceMethod(
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

  void callVoidInstanceMethod(
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

  void callVoidInstanceMethod(
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
