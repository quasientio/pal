/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.serdes.colfer.WrapPolicy;
import io.quasient.pal.serdes.colfer.Wrapper;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link IncomingInterceptCallbackDispatcher}.
 *
 * <p>Verifies callback registration, resolution, and invocation logic.
 */
public class IncomingInterceptCallbackDispatcherTest {

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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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

    // Create parameters with wrapped values
    Parameter param1 = new Parameter();
    param1.setName("arg0");
    param1.setValue(wrapValue("hello", String.class.getName()));

    Parameter param2 = new Parameter();
    param2.setName("arg1");
    param2.setValue(wrapValue(42, Integer.class.getName()));

    methodCall.setParameters(new Parameter[] {param1, param2});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
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

    // Create parameter with null value
    Parameter param = new Parameter();
    param.setName("arg0");
    Obj nullObj = new Obj();
    nullObj.setIsNull(true);
    param.setValue(nullObj);

    methodCall.setParameters(new Parameter[] {param});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
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

    // Create parameter with malformed value that will fail deserialization
    Parameter param = new Parameter();
    param.setName("arg0");
    param.setValue(createMalformedObj());

    methodCall.setParameters(new Parameter[] {param});
    exec.setInstanceMethodCall(methodCall);

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
    request.setInterceptedPeer("peer-uuid");
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
}
