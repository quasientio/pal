/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.binary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InstanceFieldPutDone;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.colfer.StaticFieldPutDone;
import com.quasient.pal.messages.types.ControlStatusType;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.rpc.AbstractRpcMessageIT;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Properties;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractColferRpcMessageIT extends AbstractRpcMessageIT
    implements ExecMessageAssertions {

  protected AbstractColferRpcMessageIT(TargetType targetType) {
    super(targetType);
  }

  protected AbstractColferRpcMessageIT() {
    this(TargetType.PEER);
  }

  @BeforeClass
  public static void initialize() throws Exception {
    logger.debug("Initializing before tests...");
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(getPalDirectoryUrl());

    // configure wiring
    AbstractModule module =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Properties appProperties = new Properties();
            appProperties.setProperty("messages.with_src_context", Boolean.toString(false));
            Names.bindProperties(binder(), appProperties);
            bind(MessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    final Properties consumerProperties = getKafkaConsumerProperties();
    final Properties producerProperties = getKafkaProducerProperties();

    // Peer launched by RpcTestSuite
    // Use the well-known shared peer UUID instead of searching for a peer
    // Retry a few times to allow for directory registration delay
    PeerInfo rpcPeer = null;
    for (int i = 0; i < 10; i++) {
      rpcPeer =
          directoryConnectionProvider
              .get()
              .orElseThrow(() -> new RuntimeException("Could not connect to directory"))
              .getPeer(com.quasient.pal.RpcTestSuite.SHARED_PEER_UUID);
      if (rpcPeer != null) {
        break;
      }
      logger.debug("Waiting for peer to register in directory (attempt {})", i + 1);
      Thread.sleep(500);
    }
    if (rpcPeer == null) {
      throw new RuntimeException("Shared RPC test peer not found in directory after 5 seconds");
    }
    thinPeer =
        new ThinPeer()
            .withUuid(clientId)
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .withLogPrefix("itt")
            .withInitialPeer(rpcPeer)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .init();
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after binary-rpc tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  private ExecMessage sendAndReceive(ExecMessage message) {
    ExecMessage response;
    try {
      if (targetType.equals(TargetType.PEER)) {
        logger.debug("Sending message to peer");
        response = thinPeer.sendToPeer(message);
        logger.debug("Received response: {}", response);
      } else {
        logger.debug("Sending message to log");
        LogMessage<Message> responseLogMessage = thinPeer.sendExecMessageToLogAndReceive(message);
        logger.debug("Received response: {}", responseLogMessage);
        response = responseLogMessage.getContent().getExecMessage();
      }
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving exec message with id: {}\n{}",
          message.getMessageId(),
          ColferUtils.format(message),
          e);
      throw e;
    }
    return response;
  }

  protected MetaMessage sendAndReceive(MetaMessage message) {
    MetaMessage response;
    try {
      response = thinPeer.sendToPeer(message);
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving meta message with id: {}\n{}",
          message.getMessageId(),
          ColferUtils.format(message),
          e);
      throw e;
    }
    return response;
  }

  protected ReturnValue callConstructor(
      String className, Class<?>[] parameterTypes, Object[] args, ObjectRef[] argObjRefs)
      throws Exception {
    return callConstructor(className, parameterTypes, args, argObjRefs, null);
  }

  protected ReturnValue callConstructor(
      String className,
      Class<?>[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs,
      String expectedThrowableType)
      throws Exception {
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }

    ExecMessage responseMessage =
        sendAndReceive(
            messageBuilder.buildNonEmptyConstructor(
                clientId, className, parameterTypesNamesArray, args, argObjRefs));

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
      assertValueIsObjectRefOfType(responseMessage.getReturnValue(), className);
    }

    return responseMessage.getReturnValue();
  }

  protected ReturnValue callEmptyConstructor(String className) throws Exception {
    return callEmptyConstructor(className, null);
  }

  private ReturnValue callEmptyConstructor(String className, String expectedThrowableType)
      throws Exception {
    ExecMessage responseMessage =
        sendAndReceive(messageBuilder.buildEmptyConstructor(clientId, className));

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
      assertValueIsObjectRefOfType(responseMessage.getReturnValue(), className);
    }

    return responseMessage.getReturnValue();
  }

  protected ReturnValue callGetStatic(String className, String fieldName) {
    return callGetStatic(className, fieldName, null);
  }

  protected ReturnValue callGetStatic(
      String className, String fieldName, String expectedThrowableType) {
    ExecMessage requestMsg = messageBuilder.buildGetStatic(clientId, className, fieldName);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
    }

    return responseMessage.getReturnValue();
  }

  protected void callPutStatic(
      String className, String fieldName, String fieldClassName, Object value) {
    callPutStatic(className, fieldName, fieldClassName, value, null);
  }

  protected void callPutStatic(
      String className,
      String fieldName,
      String fieldClassName,
      Object value,
      String expectedThrowableType) {
    ExecMessage requestMsg =
        messageBuilder.buildPutStatic(clientId, className, fieldName, fieldClassName, value);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(nullValue()));
      assertThat(responseMessage.getStaticFieldPutDone(), is(not(nullValue())));
      StaticFieldPutDone staticFieldPutDone = responseMessage.getStaticFieldPutDone();
      assertEquals(staticFieldPutDone.getField().getName(), fieldName);
    }
  }

  protected ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef) {
    return callGetInstanceVar(className, fieldName, objRef, null);
  }

  protected ReturnValue callGetInstanceVar(
      String className, String fieldName, ObjectRef objRef, String expectedThrowableType) {
    ExecMessage requestMsg = messageBuilder.buildGetObject(clientId, className, fieldName, objRef);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
    }

    return responseMessage.getReturnValue();
  }

  protected void callPutField(
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value) {
    callPutField(className, fieldName, targetObjRef, valueClassName, value, null);
  }

  protected void callPutField(
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value,
      String expectedThrowableType) {

    ExecMessage requestMsg =
        messageBuilder.buildPutObject(
            clientId, className, fieldName, targetObjRef, valueClassName, value);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(nullValue()));
      assertThat(responseMessage.getInstanceFieldPutDone(), is(not(nullValue())));
      InstanceFieldPutDone fieldPutDone = responseMessage.getInstanceFieldPutDone();
      assertEquals(fieldPutDone.getField().getName(), fieldName);
    }
  }

  protected ReturnValue callClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs) {
    return callClassMethod(
        className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected ReturnValue callClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType) {
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
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
    }

    return responseMessage.getReturnValue();
  }

  protected void callVoidClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs) {
    callVoidClassMethod(className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
  }

  protected void callVoidClassMethod(
      String className,
      String methodName,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs,
      String expectedThrowableType) {
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
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
      assertTrue(responseMessage.getReturnValue().getIsVoid());
    }
  }

  protected ReturnValue callInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs) {
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
      String expectedThrowableType) {
    ExecMessage requestMsg =
        messageBuilder.buildInstanceMethod(
            clientId,
            className,
            methodName,
            targetObjRef,
            parameterTypeNames,
            parameters,
            paramObjRefs);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
    }

    return responseMessage.getReturnValue();
  }

  protected void callVoidInstanceMethod(
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypeNames,
      Object[] parameters,
      ObjectRef[] paramObjRefs) {
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
      String expectedThrowableType) {
    ExecMessage requestMsg =
        messageBuilder.buildInstanceMethod(
            clientId,
            className,
            methodName,
            targetObjRef,
            parameterTypeNames,
            parameters,
            paramObjRefs);
    ExecMessage responseMessage = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(responseMessage, expectedThrowableType);
    } else {
      assertThat(responseMessage.getReturnValue(), is(not(nullValue())));
      assertTrue(responseMessage.getReturnValue().getIsVoid());
    }
  }

  /* Control messages */
  protected boolean sendDeleteObjectCommand(ObjectRef ref) {
    UUID peerUuid = thinPeer.getPeerUuid();
    ControlMessage msg = messageBuilder.buildDeleteObjectCommandMessage(peerUuid, ref);
    ControlMessage response = thinPeer.sendToPeer(msg);
    logger.debug("response to delete object command: {}", ColferUtils.toJson(response, true));
    ControlStatusType statusType = ControlStatusType.fromId(response.getStatus());
    return ControlStatusType.OK.equals(statusType);
  }

  protected boolean sendDeleteSessionCommand() {
    UUID sessionId = thinPeer.getPeerUuid();
    ControlMessage msg = messageBuilder.buildDeleteSessionCommandMessage(sessionId);
    ControlMessage response = thinPeer.sendToPeer(msg);
    logger.debug("response to delete session command: {}", ColferUtils.toJson(response, true));
    ControlStatusType statusType = ControlStatusType.fromId(response.getStatus());
    return ControlStatusType.OK.equals(statusType);
  }

  protected boolean sendGcCommand() {
    ControlMessage msg = messageBuilder.buildGcCommandMessage(thinPeer.getPeerUuid());
    ControlMessage response = thinPeer.sendToPeer(msg);
    logger.debug("response to GC command: {}", ColferUtils.toJson(response, true));
    ControlStatusType statusType = ControlStatusType.fromId(response.getStatus());
    return ControlStatusType.OK.equals(statusType);
  }
}
