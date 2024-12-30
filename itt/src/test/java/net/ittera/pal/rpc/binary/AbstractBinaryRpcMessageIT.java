/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.rpc.binary;

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
import java.util.Properties;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.MetaMessage;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.types.RpcType;
import net.ittera.pal.rpc.AbstractRpcMessageIT;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractBinaryRpcMessageIT extends AbstractRpcMessageIT
    implements ExecMessageAssertions {

  protected AbstractBinaryRpcMessageIT(TargetType targetType) {
    super(targetType);
  }

  protected AbstractBinaryRpcMessageIT() {
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
            bind(ObjectLookupStore.class)
                .to(ConcurrentHashMapObjectLookupStore.class)
                .asEagerSingleton();
            bind(MessageBuilder.class).asEagerSingleton();
          }
        };

    final Injector injector = Guice.createInjector(module);
    messageBuilder = injector.getInstance(MessageBuilder.class);

    final Properties consumerProperties = getKafkaConsumerProperties();
    final Properties producerProperties = getKafkaProducerProperties();

    // find a peer listening with BINARY-RPC enabled
    PeerInfo rpcPeer =
        findRpcPeer(RpcType.BINARY_RPC, directoryConnectionProvider)
            .orElseThrow(() -> new RuntimeException("No peer found with BINARY-RPC enabled"));
    thinPeer =
        new ThinPeer()
            .withUuid(clientId)
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .withInitialPeer(rpcPeer)
            .withOutboundRpcType(RpcType.BINARY_RPC)
            .init();
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  private ExecMessage sendAndReceive(ExecMessage message) {
    ExecMessage reply;
    try {
      if (targetType.equals(TargetType.PEER)) {
        logger.debug("Sending message to peer");
        reply = thinPeer.sendToPeer(message);
      } else {
        logger.debug("Sending message to log");
        reply = thinPeer.sendExecMessageToLogAndReceive(message);
      }
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving exec message with id: {}\n{}",
          message.getMessageId(),
          ColferUtils.format(message),
          e);
      throw e;
    }
    return reply;
  }

  protected MetaMessage sendAndReceive(MetaMessage message) {
    MetaMessage reply;
    try {
      reply = thinPeer.sendToPeer(message);
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving meta message with id: {}\n{}",
          message.getMessageId(),
          ColferUtils.format(message),
          e);
      throw e;
    }
    return reply;
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

    ExecMessage replyMsg =
        sendAndReceive(
            messageBuilder.buildNonEmptyConstructor(
                clientId, className, parameterTypesNamesArray, args, argObjRefs));

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
      assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
    }

    return replyMsg.getReturnValue();
  }

  protected ReturnValue callEmptyConstructor(String className) throws Exception {
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
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
      assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
    }

    return replyMsg.getReturnValue();
  }

  protected ReturnValue callGetStatic(String className, String fieldName) {
    return callGetStatic(className, fieldName, null);
  }

  protected ReturnValue callGetStatic(
      String className, String fieldName, String expectedThrowableType) {
    ExecMessage requestMsg = messageBuilder.buildGetStatic(clientId, className, fieldName);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
    }

    return replyMsg.getReturnValue();
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(nullValue()));
      assertThat(replyMsg.getStaticFieldPutDone(), is(not(nullValue())));
      StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
      assertEquals(staticFieldPutDone.getField().getName(), fieldName);
    }
  }

  protected ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef) {
    return callGetInstanceVar(className, fieldName, objRef, null);
  }

  protected ReturnValue callGetInstanceVar(
      String className, String fieldName, ObjectRef objRef, String expectedThrowableType) {
    ExecMessage requestMsg = messageBuilder.buildGetObject(clientId, className, fieldName, objRef);
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
    }

    return replyMsg.getReturnValue();
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(nullValue()));
      assertThat(replyMsg.getInstanceFieldPutDone(), is(not(nullValue())));
      InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
    }

    return replyMsg.getReturnValue();
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
      assertTrue(replyMsg.getReturnValue().getIsVoid());
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
    }

    return replyMsg.getReturnValue();
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
    ExecMessage replyMsg = sendAndReceive(requestMsg);

    // basic assertions
    if (expectedThrowableType != null) {
      assertHasThrowableOfType(replyMsg, expectedThrowableType);
    } else {
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
      assertTrue(replyMsg.getReturnValue().getIsVoid());
    }
  }
}
