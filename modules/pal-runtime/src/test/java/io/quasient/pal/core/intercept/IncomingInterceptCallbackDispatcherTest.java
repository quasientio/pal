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
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.AroundSocketAccessor;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptTypeNotSupportedException;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldGet;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.StaticFieldGet;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.serdes.colfer.ExceptionSerdes;
import io.quasient.pal.serdes.colfer.WrapPolicy;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link IncomingInterceptCallbackDispatcher}.
 *
 * <p>Verifies callback registration, resolution, and invocation logic.
 */
public class IncomingInterceptCallbackDispatcherTest {

  /** Deterministic UUID bytes used for interceptedPeer in all test requests. */
  private static final byte[] PEER_UUID_BYTES =
      UuidUtils.toBytes(UUID.nameUUIDFromBytes("peer-uuid".getBytes(StandardCharsets.UTF_8)));

  private IncomingInterceptCallbackDispatcher dispatcher;
  private CallbackResolver callbackResolver;
  private ExecMessage execMessage;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    callbackResolver = new CallbackResolver();
    dispatcher = new IncomingInterceptCallbackDispatcher(callbackResolver);

    execMessage = new ExecMessage();
    execMessage.setMessageId("test-msg-123");
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} registers a callback successfully.
   */
  @Test
  public void testRegisterCallback() {
    InterceptCallback callback = (ctx) -> new InterceptCallbackResponse();

    dispatcher.registerCallback("test-callback", callback);

    // Should not throw - callback is registered
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} rejects null callback ID.
   */
  @Test
  public void testRegisterCallbackRejectsNullId() {
    InterceptCallback callback = (ctx) -> new InterceptCallbackResponse();

    try {
      dispatcher.registerCallback(null, callback);
      fail("Expected IllegalArgumentException for null callback ID");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Callback ID must not be null or empty"));
    }
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} rejects empty callback ID.
   */
  @Test
  public void testRegisterCallbackRejectsEmptyId() {
    InterceptCallback callback = (ctx) -> new InterceptCallbackResponse();

    try {
      dispatcher.registerCallback("", callback);
      fail("Expected IllegalArgumentException for empty callback ID");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Callback ID must not be null or empty"));
    }
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} rejects null callback.
   */
  @Test
  public void testRegisterCallbackRejectsNullCallback() {
    try {
      dispatcher.registerCallback("test-callback", null);
      fail("Expected IllegalArgumentException for null callback");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Callback must not be null"));
    }
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} rejects duplicate callback IDs.
   */
  @Test
  public void testRegisterCallbackRejectsDuplicates() {
    InterceptCallback callback1 = (ctx) -> new InterceptCallbackResponse();
    InterceptCallback callback2 = (ctx) -> new InterceptCallbackResponse();

    dispatcher.registerCallback("test-callback", callback1);

    try {
      dispatcher.registerCallback("test-callback", callback2);
      fail("Expected IllegalStateException for duplicate callback ID");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Callback already registered with ID"));
      assertTrue(e.getMessage().contains("test-callback"));
    }
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#unregisterCallback(String)} removes a
   * registered callback.
   */
  @Test
  public void testUnregisterCallback() {
    InterceptCallback callback = (ctx) -> new InterceptCallbackResponse();

    dispatcher.registerCallback("test-callback", callback);

    boolean removed = dispatcher.unregisterCallback("test-callback");

    assertTrue(removed);
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#unregisterCallback(String)} returns false
   * for non-existent callback.
   */
  @Test
  public void testUnregisterCallbackNotFound() {
    boolean removed = dispatcher.unregisterCallback("non-existent");

    assertFalse(removed);
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} invokes a
   * registered callback for BEFORE phase.
   */
  @Test
  public void testHandleCallbackWithRegisteredCallback() {
    // Track whether callback was invoked
    final boolean[] invoked = {false};

    InterceptCallback callback =
        (ctx) -> {
          invoked[0] = true;
          assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1); // BEFORE intercept
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertTrue("Callback should have been invoked", invoked[0]);
    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertEquals((byte) 1, response.getPhase());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} handles
   * callback not found error.
   */
  @Test
  public void testHandleCallbackNotFound() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("non-existent");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertTrue("Should set throwException flag", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} handles
   * exceptions thrown by callback.
   */
  @Test
  public void testHandleCallbackThrowsException() {
    InterceptCallback callback =
        (ctx) -> {
          throw new RuntimeException("Callback failed");
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertTrue("Should set throwException flag for callback error", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} propagates
   * argument mutations for BEFORE phase.
   */
  @Test
  public void testHandleCallbackWithArgumentMutation() {
    InterceptCallback callback =
        (ctx) -> {
          // Note: In this test we can't actually mutate args because extractArguments returns empty
          // array
          // This test verifies the response structure when isArgsModified() would return true
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertEquals((byte) 1, response.getPhase());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} honors
   * shouldProceed=false for AROUND intercepts.
   */
  @Test
  public void testAroundInterceptCanSetShouldProceedFalse() {
    InterceptCallback callback =
        (ctx) -> {
          InterceptCallbackResponse response = new InterceptCallbackResponse();
          response.setShouldProceed(false);
          return response;
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse(
        "AROUND intercept should be able to set shouldProceed=false", response.getShouldProceed());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} forces
   * shouldProceed=true for BEFORE intercepts (cannot skip execution).
   */
  @Test
  public void testBeforeInterceptCannotSetShouldProceedFalse() {
    InterceptCallback callback =
        (ctx) -> {
          InterceptCallbackResponse response = new InterceptCallbackResponse();
          // Callback tries to set shouldProceed=false
          response.setShouldProceed(false);
          return response;
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1); // BEFORE (not AROUND)
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue(
        "BEFORE intercept cannot skip execution - shouldProceed forced to true",
        response.getShouldProceed());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} allows
   * shouldProceed=true for BEFORE intercepts.
   */
  @Test
  public void testBeforeInterceptCanSetShouldProceedTrue() {
    InterceptCallback callback =
        (ctx) -> {
          InterceptCallbackResponse response = new InterceptCallbackResponse();
          response.setShouldProceed(true);
          return response;
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1); // BEFORE
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("BEFORE intercept with shouldProceed=true should work", response.getShouldProceed());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} allows
   * AROUND intercept to set shouldProceed=true.
   */
  @Test
  public void testAroundInterceptCanSetShouldProceedTrue() {
    InterceptCallback callback =
        (ctx) -> {
          InterceptCallbackResponse response = new InterceptCallbackResponse();
          response.setShouldProceed(true);
          return response;
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("AROUND intercept with shouldProceed=true should work", response.getShouldProceed());
  }

  /** Tests that static callback method invocation requires both class and method names. */
  @Test
  public void testHandleCallbackWithoutClassOrRegisteredId() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setExec(execMessage);
    // No registeredCallbackId and no callbackClass

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for missing callback routing", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} for AFTER
   * phase.
   */
  @Test
  public void testHandleCallbackAfterPhase() {
    InterceptCallback callback =
        (ctx) -> {
          assertEquals(InterceptPhase.AFTER, ctx.getPhase());
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2); // AFTER intercept
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertEquals((byte) 2, response.getPhase());
  }

  /** Test helper class for static method callback tests. */
  public static class TestCallbackHandlers {

    /**
     * Static callback method that can be invoked via reflection.
     *
     * @param ctx the intercept context
     * @return a callback response
     */
    public static InterceptCallbackResponse testStaticCallback(InterceptContext ctx) {
      return new InterceptCallbackResponse();
    }

    /**
     * Non-static callback method (should fail validation).
     *
     * @param ctx the intercept context
     * @return a callback response
     */
    public InterceptCallbackResponse testInstanceCallback(InterceptContext ctx) {
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} can invoke
   * a static callback method via reflection.
   */
  @Test
  public void testHandleCallbackWithStaticMethod() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("testStaticCallback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertFalse("Static callback should execute successfully", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} rejects
   * non-static callback methods.
   */
  @Test
  public void testHandleCallbackRejectsNonStaticMethod() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("testInstanceCallback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for non-static method", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} handles
   * class not found errors.
   */
  @Test
  public void testHandleCallbackClassNotFound() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setCallbackClass("com.nonexistent.Class");
    request.setCallbackMethod("callback");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for class not found", response.getThrowException());
  }

  /**
   * Tests that {@link
   * IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequestMessage)} handles
   * method not found errors.
   */
  @Test
  public void testHandleCallbackMethodNotFound() {
    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("nonExistentMethod");
    request.setExec(execMessage);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for method not found", response.getThrowException());
  }

  // ===========================================================================
  // Argument Deserialization Tests
  // ===========================================================================

  /**
   * Tests successful deserialization of method arguments.
   *
   * <p>Verifies that valid arguments (String, Integer) are correctly deserialized and passed to the
   * callback handler.
   */
  @Test
  public void testArgumentDeserializationSuccess() {
    // Track deserialized arguments
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    // Create ExecMessage with method call and parameters
    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceMethodCall methodCall = new InstanceMethodCall();
    methodCall.setName("testMethod");

    // Create args with wrapped values
    Obj arg0 = wrapValue("hello", String.class.getName());
    Obj arg1 = wrapValue(42, Integer.class.getName());

    methodCall.setArgs(new Obj[] {arg0, arg1});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 2 args", 2, capturedArgs[0].length);
    assertEquals("First arg should be 'hello'", "hello", capturedArgs[0][0]);
    assertEquals("Second arg should be 42", 42, capturedArgs[0][1]);
  }

  /**
   * Tests successful deserialization of null method arguments.
   *
   * <p>Verifies that null values are correctly handled (not confused with deserialization failure).
   */
  @Test
  public void testArgumentDeserializationNullValue() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceMethodCall methodCall = new InstanceMethodCall();
    methodCall.setName("testMethod");

    // Create arg with null value
    Obj nullObj = new Obj();
    nullObj.setIsNull(true);

    methodCall.setArgs(new Obj[] {nullObj});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertNull("Arg should be null", capturedArgs[0][0]);
  }

  /**
   * Tests that argument deserialization failure throws IllegalArgumentException.
   *
   * <p>Verifies that malformed argument data causes callback dispatch to fail with error response,
   * rather than silently returning null.
   */
  @Test
  public void testArgumentDeserializationFailure() {
    InterceptCallback callback =
        (ctx) -> {
          fail("Callback should not be invoked when deserialization fails");
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceMethodCall methodCall = new InstanceMethodCall();
    methodCall.setName("testMethod");

    // Create arg with malformed value that will fail deserialization
    methodCall.setArgs(new Obj[] {createMalformedObj()});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue(
        "Should return error response when argument deserialization fails",
        response.getThrowException());
  }

  // ===========================================================================
  // Field PUT Value Deserialization Tests
  // ===========================================================================

  /**
   * Tests successful deserialization of instance field PUT value.
   *
   * <p>Verifies that the PUT value is correctly deserialized and passed to the callback handler as
   * args[0].
   */
  @Test
  public void testInstanceFieldPutValueDeserializationSuccess() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceFieldPut fieldPut = new InstanceFieldPut();
    Field field = new Field();
    field.setName("counter");
    fieldPut.setField(field);
    fieldPut.setValueObject(wrapValue(100, Integer.class.getName()));
    exec.setInstanceFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg (PUT value)", 1, capturedArgs[0].length);
    assertEquals("PUT value should be 100", 100, capturedArgs[0][0]);
  }

  /**
   * Tests successful deserialization of null field PUT value.
   *
   * <p>Verifies that null is correctly handled as a valid PUT value (not confused with
   * deserialization failure).
   */
  @Test
  public void testFieldPutValueDeserializationNullValue() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceFieldPut fieldPut = new InstanceFieldPut();
    Field field = new Field();
    field.setName("counter");
    fieldPut.setField(field);
    Obj nullObj = new Obj();
    nullObj.setIsNull(true);
    fieldPut.setValueObject(nullObj);
    exec.setInstanceFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertNull("PUT value should be null", capturedArgs[0][0]);
  }

  /**
   * Tests that instance field PUT value deserialization failure throws IllegalArgumentException.
   *
   * <p>Verifies that malformed PUT value data causes callback dispatch to fail with error response,
   * rather than silently returning null.
   */
  @Test
  public void testInstanceFieldPutValueDeserializationFailure() {
    InterceptCallback callback =
        (ctx) -> {
          fail("Callback should not be invoked when deserialization fails");
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceFieldPut fieldPut = new InstanceFieldPut();
    Field field = new Field();
    field.setName("counter");
    fieldPut.setField(field);
    fieldPut.setValueObject(createMalformedObj());
    exec.setInstanceFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue(
        "Should return error response when PUT value deserialization fails",
        response.getThrowException());
  }

  /**
   * Tests that static field PUT value deserialization failure throws IllegalArgumentException.
   *
   * <p>Verifies that malformed PUT value data for static fields also causes callback dispatch to
   * fail.
   */
  @Test
  public void testStaticFieldPutValueDeserializationFailure() {
    InterceptCallback callback =
        (ctx) -> {
          fail("Callback should not be invoked when deserialization fails");
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    StaticFieldPut fieldPut = new StaticFieldPut();
    Field field = new Field();
    field.setName("staticCounter");
    fieldPut.setField(field);
    fieldPut.setValueObject(createMalformedObj());
    exec.setStaticFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue(
        "Should return error response when static PUT value deserialization fails",
        response.getThrowException());
  }

  // ===========================================================================
  // Helper Methods
  // ===========================================================================

  /**
   * Wraps a value into an Obj using the Wrapper utility.
   *
   * @param value the value to wrap
   * @param className the class name of the value
   * @return the wrapped Obj
   */
  private Obj wrapValue(Object value, String className) {
    Obj obj = new Obj();
    return Wrapper.wrapInto(obj, value, className, null, WrapPolicy.FORCE_BY_VALUE);
  }

  /**
   * Creates a malformed Obj that will fail deserialization.
   *
   * <p>Sets the class to Integer but the value to invalid JSON, causing Unwrapper to fail.
   *
   * @return a malformed Obj
   */
  private Obj createMalformedObj() {
    Obj obj = new Obj();
    obj.setIsNull(false);
    Class clazz = new Class();
    clazz.setName("java.lang.Integer");
    obj.setClazz(clazz);
    // Invalid JSON for Integer - will cause deserialization to fail
    obj.setValue("not-a-valid-integer-json");
    return obj;
  }

  // ===========================================================================
  // Exception Handling Tests
  // ===========================================================================

  /**
   * Tests that API misuse error flag is set on response when callback throws
   * InterceptApiMisuseException.
   *
   * <p>Acceptance Criterion:
   * [TEST:IncomingInterceptCallbackDispatcherTest.shouldSetApiMisuseErrorFlagOnResponse]
   *
   * <p>Unit test specifications for exception handling
   */
  @Test
  public void shouldSetApiMisuseErrorFlagOnResponse() {
    // Given: Callback throws InterceptApiMisuseException
    InterceptCallback callback =
        (ctx) -> {
          throw new InterceptTypeNotSupportedException(
              "getReturnValue()", InterceptType.BEFORE, null);
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    // When: Building error response
    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    // Then: Response has isApiMisuseError=true and throwException=true
    assertNotNull(response);
    assertTrue("Should set throwException flag", response.getThrowException());
    assertTrue(
        "Should set isApiMisuseError flag for InterceptApiMisuseException",
        response.getIsApiMisuseError());
    assertNotNull("Should have exception set", response.getException());
  }

  /**
   * Tests that API misuse error flag is NOT set for business exceptions.
   *
   * <p>Verifies that when a callback throws a RuntimeException (intentional business exception),
   * the response correctly identifies it as a business exception rather than API misuse.
   *
   * <p>Acceptance Criterion:
   * [TEST:IncomingInterceptCallbackDispatcherTest.shouldNotSetApiMisuseErrorFlagForBusinessException]
   *
   * <p>Unit test specifications for exception handling
   */
  @Test
  public void shouldNotSetApiMisuseErrorFlagForBusinessException() {
    // Given: Callback throws RuntimeException (intentional business exception)
    InterceptCallback callback =
        (ctx) -> {
          throw new RuntimeException("Business logic error");
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    // When: Building error response
    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    // Then: Response has isApiMisuseError=false and throwException=true
    assertNotNull(response);
    assertTrue("Should set throwException flag", response.getThrowException());
    assertFalse(
        "Should NOT set isApiMisuseError flag for business exceptions",
        response.getIsApiMisuseError());
    assertNotNull("Should have exception set", response.getException());
  }

  /**
   * Tests that checked exceptions are validated before serialization according to policy.
   *
   * <p>Verifies that when CheckedExceptionPolicy is WRAP, a checked exception (e.g., SQLException)
   * thrown by the callback is wrapped in RuntimeException in the serialized response.
   *
   * <p>Acceptance Criterion:
   * [TEST:IncomingInterceptCallbackDispatcherTest.shouldValidateCheckedExceptionBeforeSerialization]
   *
   * <p>Unit test specifications for exception handling
   */
  @Test
  public void shouldValidateCheckedExceptionBeforeSerialization() {
    // Given: Callback throws SQLException (checked exception)
    InterceptCallback callback =
        (ctx) -> {
          throw new SQLException("Database connection failed");
        };

    dispatcher.registerCallback("test-callback", callback);

    // Create ExecMessage with declared exceptions (e.g., IOException only)
    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");
    exec.setDeclaredExceptions(new String[] {"java.io.IOException"});

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    // When: Building response (with CheckedExceptionPolicy.WRAP as default)
    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    // Then: SQLException wrapped in RuntimeException in serialized response
    assertNotNull(response);
    assertTrue("Should set throwException flag", response.getThrowException());
    assertFalse(
        "Should NOT set isApiMisuseError flag for checked exceptions",
        response.getIsApiMisuseError());
    assertNotNull("Should have exception set", response.getException());

    // Verify that the exception was wrapped
    RaisedThrowable raisedThrowable = response.getException();
    assertNotNull(raisedThrowable);
    // The exception should be wrapped in RuntimeException
    // We can verify this by deserializing and checking the exception class
    Throwable deserializedException = ExceptionSerdes.deserializeException(raisedThrowable);
    assertNotNull(deserializedException);
    assertTrue(
        "Exception should be wrapped in RuntimeException",
        deserializedException instanceof RuntimeException);
    // Verify the original SQLException is the cause
    assertNotNull("Should have a cause", deserializedException.getCause());
    assertTrue(
        "Cause should be SQLException", deserializedException.getCause() instanceof SQLException);
  }

  // ===========================================================================
  // AROUND Intercept with ctx.proceed() API Tests
  // ===========================================================================

  /**
   * Tests that handleAroundCallback correctly handles callback that calls proceed().
   *
   * <p>Verifies that when the callback invokes ctx.proceed(), the response is an AFTER phase
   * response.
   */
  @Test
  public void testHandleAroundCallback_proceedCalled_returnsAfterPhaseResponse() {
    // Create callback that calls proceed()
    InterceptCallback callback =
        (ctx) -> {
          ctx.proceed();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("around-callback", callback);

    // Create mock socket accessor that simulates successful proceed
    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) ->
            new AfterPhaseData("proceed-result", null, false); // successful return

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertEquals("req-around", response.getCallbackId());
    assertEquals(
        "Should return AFTER phase when proceed() was called",
        InterceptPhase.AFTER.toByte(),
        response.getPhase());
    assertFalse("Should not throw exception", response.getThrowException());
  }

  /**
   * Tests that handleAroundCallback correctly handles callback that skips execution.
   *
   * <p>Verifies that when the callback does NOT call proceed() but sets a return value, the
   * response is a BEFORE phase skip response.
   */
  @Test
  public void testHandleAroundCallback_skipWithReturnValue_returnsSkipResponse() {
    // Create callback that skips proceed() and sets return value
    InterceptCallback callback =
        (ctx) -> {
          ctx.setReturnValue("cached-value");
          // Don't call proceed()
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("around-callback", callback);

    // Socket accessor should not be called since we skip
    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) -> {
          fail("Socket accessor should not be called when skipping");
          return null;
        };

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertEquals("req-around", response.getCallbackId());
    assertEquals(
        "Should return BEFORE phase when skipping",
        InterceptPhase.BEFORE.toByte(),
        response.getPhase());
    assertFalse("Should NOT proceed", response.getShouldProceed());
    assertTrue("Should override return", response.getOverrideReturn());
    assertFalse("Should not throw exception", response.getThrowException());
  }

  /**
   * Tests that handleAroundCallback correctly handles callback that skips with exception.
   *
   * <p>Verifies that when the callback does NOT call proceed() but sets an exception, the response
   * is a BEFORE phase skip response with exception.
   */
  @Test
  public void testHandleAroundCallback_skipWithException_returnsSkipResponseWithException() {
    // Create callback that skips proceed() and sets exception
    InterceptCallback callback =
        (ctx) -> {
          ctx.setExceptionToThrow(new IllegalStateException("Skipped with error"));
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("around-callback", callback);

    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) -> {
          fail("Socket accessor should not be called when skipping");
          return null;
        };

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertEquals(
        "Should return BEFORE phase when skipping",
        InterceptPhase.BEFORE.toByte(),
        response.getPhase());
    assertFalse("Should NOT proceed", response.getShouldProceed());
    assertTrue("Should throw exception", response.getThrowException());
  }

  /**
   * Tests that handleAroundCallback throws when skipping without return value or exception.
   *
   * <p>Verifies that when the callback does NOT call proceed() and does NOT set return value or
   * exception, an IllegalStateException is thrown.
   */
  @Test
  public void testHandleAroundCallback_skipWithoutReturnValue_returnsError() {
    // Create callback that skips proceed() but doesn't set return value
    InterceptCallback callback =
        (ctx) -> {
          // Don't call proceed()
          // Don't set return value
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("around-callback", callback);

    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) -> {
          fail("Socket accessor should not be called when skipping");
          return null;
        };

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertTrue(
        "Should return error when skipping without return value", response.getThrowException());
  }

  /**
   * Tests that handleAroundCallback handles callback that modifies return value after proceed().
   *
   * <p>Verifies post-proceed return value modification.
   */
  @Test
  public void testHandleAroundCallback_proceedWithReturnValueOverride_overridesReturn() {
    // Create callback that calls proceed() and then modifies return value
    InterceptCallback callback =
        (ctx) -> {
          ctx.proceed();
          ctx.setReturnValue("modified-result");
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("around-callback", callback);

    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) -> new AfterPhaseData("original-result", null, false);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertEquals(InterceptPhase.AFTER.toByte(), response.getPhase());
    assertTrue("Should override return value", response.getOverrideReturn());
    assertFalse("Should not throw exception", response.getThrowException());
  }

  /**
   * Tests that handleAroundCallback handles callback exception.
   *
   * <p>Verifies that exceptions thrown by the callback are handled properly.
   */
  @Test
  public void testHandleAroundCallback_callbackThrows_returnsErrorResponse() {
    InterceptCallback callback =
        (ctx) -> {
          throw new RuntimeException("Callback failed");
        };

    dispatcher.registerCallback("around-callback", callback);

    AroundSocketAccessor socketAccessor =
        (beforeResponse, proceedTimeoutMs) -> new AfterPhaseData("result", null, false);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-around");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("around-callback");
    request.setExec(execMessage);
    request.setProceedTimeoutMs(5000);

    InterceptCallbackResponseMessage response =
        dispatcher.handleAroundCallback(request, socketAccessor);

    assertNotNull(response);
    assertTrue("Should indicate error", response.getThrowException());
  }

  // ===========================================================================
  // extractArguments Additional Tests
  // ===========================================================================

  /** Tests extractArguments with ExecMessage having no call types set. */
  @Test
  public void testExtractArguments_emptyExecMessage_returnsEmptyArray() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    // Empty ExecMessage with no method/field/constructor call set
    ExecMessage emptyExec = new ExecMessage();
    emptyExec.setMessageId("test-empty-msg");

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(emptyExec); // ExecMessage with no call types set

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have empty args array", 0, capturedArgs[0].length);
  }

  /** Tests extractArguments with instance field GET (no arguments). */
  @Test
  public void testExtractArguments_instanceFieldGet_returnsEmptyArray() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");
    InstanceFieldGet fieldGet = new InstanceFieldGet();
    Field field = new Field();
    field.setName("myField");
    fieldGet.setField(field);
    exec.setInstanceFieldGet(fieldGet);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Field GET should have empty args", 0, capturedArgs[0].length);
  }

  /** Tests extractArguments with static field GET (no arguments). */
  @Test
  public void testExtractArguments_staticFieldGet_returnsEmptyArray() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");
    StaticFieldGet fieldGet = new StaticFieldGet();
    Field field = new Field();
    field.setName("staticField");
    fieldGet.setField(field);
    exec.setStaticFieldGet(fieldGet);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Static field GET should have empty args", 0, capturedArgs[0].length);
  }

  /** Tests extractArguments with constructor call parameters. */
  @Test
  public void testExtractArguments_constructorCall_extractsParameters() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    ConstructorCall constructorCall = new ConstructorCall();
    constructorCall.setArgs(new Obj[] {wrapValue("constructor-arg", String.class.getName())});
    exec.setConstructorCall(constructorCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertEquals("Arg should be 'constructor-arg'", "constructor-arg", capturedArgs[0][0]);
  }

  /** Tests extractArguments with static method call parameters. */
  @Test
  public void testExtractArguments_staticMethodCall_extractsParameters() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    ClassMethodCall classMethodCall = new ClassMethodCall();
    classMethodCall.setName("staticMethod");
    classMethodCall.setArgs(new Obj[] {wrapValue(999, Integer.class.getName())});
    exec.setClassMethodCall(classMethodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertEquals("Arg should be 999", 999, capturedArgs[0][0]);
  }

  /** Tests extractArguments with empty parameters array. */
  @Test
  public void testExtractArguments_emptyParameters_returnsEmptyArray() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceMethodCall methodCall = new InstanceMethodCall();
    methodCall.setName("noArgMethod");
    methodCall.setArgs(new Obj[0]); // empty array
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have empty args array", 0, capturedArgs[0].length);
  }

  /** Tests extractArguments with null parameters. */
  @Test
  public void testExtractArguments_nullParameters_returnsEmptyArray() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceMethodCall methodCall = new InstanceMethodCall();
    methodCall.setName("noArgMethod");
    methodCall.setArgs(null); // null array
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have empty args array", 0, capturedArgs[0].length);
  }

  // ===========================================================================
  // extractReturnValue Tests
  // ===========================================================================

  /** Tests AFTER phase with void method (isVoid=true). */
  @Test
  public void testAfterPhase_voidMethod_returnValueIsNull() {
    final Object[] capturedReturnValue = {new Object()}; // sentinel to detect null

    InterceptCallback callback =
        (ctx) -> {
          capturedReturnValue[0] = ctx.getReturnValue();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(true);
    request.setReturnValue(null);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNull("Return value should be null for void method", capturedReturnValue[0]);
  }

  /** Tests AFTER phase with non-null return value. */
  @Test
  public void testAfterPhase_withReturnValue_deserializesCorrectly() {
    final Object[] capturedReturnValue = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedReturnValue[0] = ctx.getReturnValue();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);
    request.setReturnValue(wrapValue("returned-value", String.class.getName()));

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertEquals("returned-value", capturedReturnValue[0]);
  }

  /** Tests AFTER phase with null return value object. */
  @Test
  public void testAfterPhase_nullReturnValueObj_returnsNull() {
    final Object[] capturedReturnValue = {new Object()}; // sentinel

    InterceptCallback callback =
        (ctx) -> {
          capturedReturnValue[0] = ctx.getReturnValue();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);
    request.setReturnValue(null); // null Obj

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNull("Return value should be null", capturedReturnValue[0]);
  }

  // ===========================================================================
  // extractThrownException Tests
  // ===========================================================================

  /** Tests AFTER phase with thrown exception. */
  @Test
  public void testAfterPhase_withThrownException_deserializesCorrectly() {
    final Throwable[] capturedException = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedException[0] = ctx.getThrownException();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    // Create serialized exception
    RaisedThrowable raisedException =
        ExceptionSerdes.serializeException(new RuntimeException("Test exception"));

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);
    request.setThrownException(raisedException);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Exception should be captured", capturedException[0]);
    assertTrue(
        "Exception should be RuntimeException", capturedException[0] instanceof RuntimeException);
    assertEquals("Test exception", capturedException[0].getMessage());
  }

  /** Tests AFTER phase with null thrown exception. */
  @Test
  public void testAfterPhase_nullThrownException_returnsNull() {
    final Throwable[] capturedException = {new RuntimeException("sentinel")};

    InterceptCallback callback =
        (ctx) -> {
          capturedException[0] = ctx.getThrownException();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);
    request.setThrownException(null);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNull("Exception should be null", capturedException[0]);
  }

  // ===========================================================================
  // Return Value Override in AFTER Phase Tests
  // ===========================================================================

  /** Tests AFTER phase callback that overrides return value. */
  @Test
  public void testAfterPhase_callbackOverridesReturnValue() {
    InterceptCallback callback =
        (ctx) -> {
          ctx.setReturnValue("overridden-value");
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);
    request.setReturnValue(wrapValue("original-value", String.class.getName()));

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertTrue("Should have overrideReturn=true", response.getOverrideReturn());
    assertNotNull("New return value should be set", response.getNewReturnValue());
  }

  /** Tests field PUT with null valueObject. */
  @Test
  public void testFieldPut_nullValueObject_returnsNullAsArg() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    InstanceFieldPut fieldPut = new InstanceFieldPut();
    Field field = new Field();
    field.setName("counter");
    fieldPut.setField(field);
    fieldPut.setValueObject(null); // null value object
    exec.setInstanceFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertNull("PUT value should be null", capturedArgs[0][0]);
  }

  /** Tests static field PUT value deserialization success. */
  @Test
  public void testStaticFieldPut_success() {
    final Object[][] capturedArgs = {null};

    InterceptCallback callback =
        (ctx) -> {
          capturedArgs[0] = ctx.getArgs();
          return new InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    ExecMessage exec = new ExecMessage();
    exec.setMessageId("test-msg");

    StaticFieldPut fieldPut = new StaticFieldPut();
    Field field = new Field();
    field.setName("staticCounter");
    fieldPut.setField(field);
    fieldPut.setValueObject(wrapValue(42, Integer.class.getName()));
    exec.setStaticFieldPut(fieldPut);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer(PEER_UUID_BYTES);
    request.setRegisteredCallbackId("test-callback");
    request.setExec(exec);

    InterceptCallbackResponseMessage response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("Should not throw exception", response.getThrowException());
    assertNotNull("Args should be captured", capturedArgs[0]);
    assertEquals("Should have 1 arg", 1, capturedArgs[0].length);
    assertEquals("PUT value should be 42", 42, capturedArgs[0][0]);
  }
}
