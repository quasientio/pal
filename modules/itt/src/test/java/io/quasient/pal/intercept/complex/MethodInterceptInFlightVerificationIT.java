/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.complex;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A second, 'verifier' ThinPeer is used, so two in total: one for invoking a call (to a method or
 * field op) and a second one to send messages to the interceptable peer before/after callbacks in
 * order to verify state object/class values.
 */
public class MethodInterceptInFlightVerificationIT extends AbstractInterceptIT {

  private ThinPeer verifierThinPeer;

  @Before
  public void setUp() throws Exception {
    logger.info("===== MethodInterceptInFlightVerificationIT.setUp: STARTING =====");

    // a 2nd ThinPeer to be used for verifications from callback threads
    this.verifierThinPeer =
        new ThinPeer()
            .withUuid(UUID.randomUUID())
            .withName("Verifier")
            .withInitialPeer(interceptablePeerInfo)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();

    logger.info("===== MethodInterceptInFlightVerificationIT.setUp: COMPLETED =====");
  }

  @After
  public void tearDown() throws Exception {
    logger.info("===== MethodInterceptInFlightVerificationIT.tearDown: STARTING =====");

    logger.info("Closing verifierThinPeer");
    verifierThinPeer.close();
    logger.info("===== MethodInterceptInFlightVerificationIT.tearDown: COMPLETED =====");
  }

  @Test
  public void testBeforeInstanceMethod() throws Exception {
    logger.info("===== testBeforeInstanceMethod: TEST STARTED =====");

    final InterceptType interceptType = InterceptType.BEFORE;

    // args to call
    final int N = 2;
    final int factor = 5;

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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
          assertThat(
              callback.getInterceptCallbackRequestMessage().getCallbackClass(), is(callbackClass));
          assertThat(
              callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
              is(callbackMethod));
          assertThat(
              callback
                  .getInterceptCallbackRequestMessage()
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
