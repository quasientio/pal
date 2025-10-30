/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.Unwrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import org.junit.Test;

public class MethodInterceptIT extends AbstractInterceptIT {

  @Test
  public void testBeforeInstanceMethod() throws Exception {
    logger.info("===== testBeforeInstanceMethod: TEST STARTED =====");

    final InterceptType interceptType = InterceptType.BEFORE;

    // args to call
    final int N = 2;
    final int factor = 5;

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    // register intercept
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            interceptType,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyBy", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // give it some time so listening peer gets intercept request
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // create app instance
    logger.info("Creating InterceptableApp instance");
    ObjectRef interceptableAppInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("InterceptableApp instance created with ref: {}", interceptableAppInstance);

    // receive callbacks and verify class/object state in separate thread
    List<Message> callbacks = new ArrayList<>();
    logger.info("Starting executor thread to receive {} callbacks", N);
    Future<?> future =
        executor.submit(
            () -> {
              logger.info("Executor thread started, waiting for callbacks");
              Message callback =
                  receiveCallbackVerifyAndResponse(
                      () -> {
                        ReturnValue retValue =
                            invoke(
                                    messageBuilder.buildGetObject(
                                        myPeerUuid,
                                        InterceptableApp.class.getName(),
                                        "counter",
                                        interceptableAppInstance),
                                    verifierThinPeer)
                                .getReturnValue();

                        try {
                          assertValueIsObjectOfType(retValue, "java.lang.Integer");
                        } catch (ClassNotFoundException e) {
                          logger.error("Error asserting object type", e);
                          return new AssertionError("Error asserting object type");
                        }
                        Object unwrappedObj;
                        try {
                          unwrappedObj = Unwrapper.unwrapObject(retValue.getObject());
                        } catch (ClassNotFoundException e) {
                          logger.error("Error unwrapping object", e);
                          return new AssertionError("Error unwrapping object");
                        }
                        if (!(unwrappedObj instanceof Integer intObj) || intObj != 1) {
                          return new AssertionError("Expected Integer value = 1");
                        }
                        return null;
                      });
              callbacks.add(callback);
              logger.debug("first callback received");

              callback =
                  receiveCallbackVerifyAndResponse(
                      () -> {
                        ReturnValue retValue =
                            invoke(
                                    messageBuilder.buildGetObject(
                                        myPeerUuid,
                                        InterceptableApp.class.getName(),
                                        "counter",
                                        interceptableAppInstance),
                                    verifierThinPeer)
                                .getReturnValue();
                        try {
                          assertValueIsObjectOfType(retValue, "java.lang.Integer");
                        } catch (ClassNotFoundException e) {
                          logger.error("Error asserting object type", e);
                          return new AssertionError("Error asserting object type");
                        }
                        Object unwrappedObj;
                        try {
                          unwrappedObj = Unwrapper.unwrapObject(retValue.getObject());
                        } catch (ClassNotFoundException e) {
                          logger.error("Error unwrapping object", e);
                          return new AssertionError("Error unwrapping object");
                        }
                        if (!(unwrappedObj instanceof Integer intObj) || intObj != 5) {
                          return new AssertionError("Expected Integer value = 5");
                        }
                        return null;
                      });
              callbacks.add(callback);
              logger.debug("second callback received");
              logger.info("Executor thread completed, both callbacks received");
            });

    // call a method that triggers N calls to the method we intercept, so we expect N callbacks
    logger.info(
        "Invoking multiplyCounterNTimesBy(N={}, factor={}) which should trigger {} callbacks",
        N,
        factor,
        N);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            interceptableAppInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {N, factor}));
    logger.info("multiplyCounterNTimesBy invocation completed, now waiting for executor to finish");

    logger.info("Calling future.get() to wait for callbacks");
    future.get();
    logger.info("future.get() returned, executor thread completed");

    // verify callback contents
    callbacks.forEach(
        callback -> {
          assertThat(callback, is(not(nullValue())));
          assertThat(callback.getMessageType(), is(MessageType.EXEC_CLASS_METHOD.getId()));
          assertThat(
              callback.getExecMessage().getClassMethodCall().getClazz().getName(),
              is(callbackClass));
          assertThat(callback.getExecMessage().getClassMethodCall().getName(), is(callbackMethod));
          assertThat(callback.getExecMessage().getClassMethodCall().getParameters().length, is(1));
        });

    // throw AssertionError saved from class/object state verification
    if (assertionError != null) {
      logger.error("Test failed with assertion error: {}", assertionError.getMessage());
      throw assertionError;
    }

    logger.info("===== testBeforeInstanceMethod: TEST COMPLETED SUCCESSFULLY =====");
  }
}
