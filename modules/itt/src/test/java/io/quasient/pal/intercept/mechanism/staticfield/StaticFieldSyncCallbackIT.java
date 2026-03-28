/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.intercept.mechanism.staticfield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for synchronous static field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the callback mechanism for synchronous intercepts on static field
 * operations (EXEC_GET_STATIC and EXEC_PUT_STATIC), including single callbacks for both BEFORE and
 * AFTER intercept types.
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (getter/setter
 *       method accesses static field)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 */
@RunWith(Parameterized.class)
public class StaticFieldSyncCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public StaticFieldSyncCallbackIT(InvocationPath path) {
    this.path = path;
  }

  /**
   * Returns the parameterized test data for invocation paths.
   *
   * @return collection of invocation path parameters
   */
  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  /**
   * Invokes a GET on the staticCounter field through the specified invocation path.
   *
   * @return the response ExecMessage
   */
  private ExecMessage invokeStaticFieldGet() {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use getter method that accesses static field
      return invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getStaticCounter",
              new String[] {},
              null,
              null,
              new Object[] {}));
    } else {
      // INCOMING_RPC: Access static field directly
      return invoke(
          messageBuilder.buildGetStatic(
              myPeerUuid, InterceptableApp.class.getName(), "staticCounter"));
    }
  }

  /**
   * Invokes a PUT on the staticCounter field through the specified invocation path.
   *
   * @param value the value to set
   * @return the response ExecMessage
   */
  private ExecMessage invokeStaticFieldPut(int value) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use setter method that accesses static field
      return invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setStaticCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {value}));
    } else {
      // INCOMING_RPC: Access static field directly
      return invoke(
          messageBuilder.buildPutStatic(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "staticCounter",
              "java.lang.Integer",
              value));
    }
  }

  /**
   * Tests single BEFORE callback on static field GET operation.
   *
   * <p>Registers a BEFORE intercept on staticCounter, invokes a field GET, and verifies exactly 1
   * callback is received with correct structure.
   */
  @Test
  public void testSingleBeforeCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnGet [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register a BEFORE intercept on staticCounter field GET
    logger.info("Creating BEFORE intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke static field GET through the specified path
    logger.info("Invoking staticCounter GET via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeStaticFieldGet();
    logger.info("staticCounter GET invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // Verify the intercepted operation is a static field GET
    assertThat(
        "Intercepted operation should be StaticFieldGet",
        callback.getInterceptCallbackRequestMessage().getExec().getStaticFieldGet(),
        is(notNullValue()));

    logger.info(
        "===== testSingleBeforeCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER callback on static field GET operation.
   *
   * <p>Registers an AFTER intercept on staticCounter, invokes a field GET, and verifies exactly 1
   * callback is received after the field get.
   */
  @Test
  public void testSingleAfterCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterCallbackOnGet [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register an AFTER intercept on staticCounter field GET
    logger.info("Creating AFTER intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke static field GET through the specified path
    logger.info("Invoking staticCounter GET via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeStaticFieldGet();
    logger.info("staticCounter GET invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // AFTER GET callbacks wrap the ReturnValue (the field value that was read)
    assertThat(
        "Intercepted operation should have ReturnValue",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "The return value should not be void",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have field info",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getReturnValue()
            .getFrom()
            .getField(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single BEFORE callback on static field PUT operation.
   *
   * <p>Registers a BEFORE intercept on staticCounter, invokes a field PUT, and verifies exactly 1
   * callback is received with correct structure.
   */
  @Test
  public void testSingleBeforeCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register a BEFORE intercept on staticCounter field PUT
    logger.info("Creating BEFORE intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke static field PUT through the specified path
    logger.info("Invoking staticCounter PUT via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeStaticFieldPut(newValue);
    logger.info("staticCounter PUT invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // BEFORE PUT callback wraps the PUT operation
    assertThat(
        "Callback should be StaticFieldPut",
        callback.getInterceptCallbackRequestMessage().getExec().getStaticFieldPut(),
        is(notNullValue()));

    // Verify the value being PUT matches what we passed
    Obj putValueObj =
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getStaticFieldPut()
            .getValueObject();
    Object value = Unwrapper.unwrapObject(putValueObj);
    assertThat("PUT value should match the value we set", value, is(newValue));

    logger.info(
        "===== testSingleBeforeCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER callback on static field PUT operation.
   *
   * <p>Registers an AFTER intercept on staticCounter, invokes a field PUT, and verifies exactly 1
   * callback is received after the field put.
   */
  @Test
  public void testSingleAfterCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register an AFTER intercept on staticCounter field PUT
    logger.info("Creating AFTER intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke static field PUT through the specified path
    logger.info("Invoking staticCounter PUT via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeStaticFieldPut(newValue);
    logger.info("staticCounter PUT invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    assertThat(
        "Callback should have StaticFieldPutDone",
        callback.getInterceptCallbackRequestMessage().getExec().getStaticFieldPutDone(),
        is(notNullValue()));
    assertThat(
        "StaticFieldPutDone should have field info",
        callback.getInterceptCallbackRequestMessage().getExec().getStaticFieldPutDone().getField(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
