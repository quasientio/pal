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
import java.util.UUID;
import net.ittera.pal.AbstractIntegrationTest;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBinaryRPCMessageIT extends AbstractIntegrationTest
    implements ExecMessageAssertions {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  protected static final UUID clientId = UUID.randomUUID();

  protected static MessageBuilder messageBuilder;
  protected static ThinPeer thinPeer;

  @BeforeClass
  public static void initialize() throws Exception {

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

    thinPeer =
        new ThinPeer()
            .withUuid(clientId)
            .withDirectoryUrl(getPalDirectoryUrl())
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .init();
  }

  @AfterClass
  public static void finalizeStuff() {
    logger.debug("Finalizing after tests...");
    if (thinPeer != null) {
      thinPeer.close();
    }
  }

  private ExecMessage sendAndReceive(ExecMessage message) throws Exception {
    ExecMessage reply;
    try {
      reply = thinPeer.sendAndReceive(message);
    } catch (Exception e) {
      logger.error(
          "Exception sending/receiving message with uuid: {}\n{}",
          message.getMessageUuid(),
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
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
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
      assertThat(replyMsg.getReturnValue(), is(nullValue()));
      assertThat(replyMsg.getStaticFieldPutDone(), is(not(nullValue())));
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
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
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
      assertThat(replyMsg.getReturnValue(), is(not(nullValue())));
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
