/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.mechanism;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    logger.info("multiplyCounterNTimesBy invocation completed");

    // Receive callbacks using getCallbacks()
    logger.info("Receiving {} callbacks using getCallbacks()", N);
    List<Message> callbacks = getCallbacks(N, 5000);
    logger.info("All {} callbacks received successfully", N);

    // verify callback contents
    callbacks.forEach(
        callback -> {
          assertThat(callback, is(not(nullValue())));
          assertThat(callback.getMessageType(), is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
          assertThat(callback.getInterceptCallbackRequest().getCallbackClass(), is(callbackClass));
          assertThat(
              callback.getInterceptCallbackRequest().getCallbackMethod(), is(callbackMethod));
          assertThat(
              callback
                  .getInterceptCallbackRequest()
                  .getExec()
                  .getInstanceMethodCall()
                  .getParameters()
                  .length,
              is(1));
        });

    // Verify class/object state after callbacks
    logger.info("Verifying object state after callbacks");

    // First callback should have been triggered when counter was 1
    ReturnValue retValue1 =
        invoke(
                messageBuilder.buildGetObject(
                    myPeerUuid,
                    InterceptableApp.class.getName(),
                    "counter",
                    interceptableAppInstance),
                verifierThinPeer)
            .getReturnValue();
    assertValueIsObjectOfType(retValue1, "java.lang.Integer");
    Object unwrappedObj1 = Unwrapper.unwrapObject(retValue1.getObject());
    // After N=2 multiplications by factor=5, counter should be 1 * 5 * 5 = 25
    assertThat(
        "Counter should be " + (int) Math.pow(factor, N) + " after " + N + " multiplications",
        unwrappedObj1,
        is((int) Math.pow(factor, N)));

    logger.info("===== testBeforeInstanceMethod: TEST COMPLETED SUCCESSFULLY =====");
  }
}
