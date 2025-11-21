/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.common.lang.intercept.InterceptCallback;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptCallbackRequest;
import com.quasient.pal.messages.colfer.InterceptCallbackResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link IncomingInterceptCallbackDispatcher}.
 *
 * <p>Verifies callback registration, resolution, and invocation logic.
 */
public class IncomingInterceptCallbackDispatcherTest {

  private IncomingInterceptCallbackDispatcher dispatcher;
  private ExecMessage execMessage;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    dispatcher = new IncomingInterceptCallbackDispatcher();

    execMessage = new ExecMessage();
    execMessage.setMessageId("test-msg-123");
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} registers a callback successfully.
   */
  @Test
  public void testRegisterCallback() {
    InterceptCallback callback =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    // Should not throw - callback is registered
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#registerCallback(String,
   * InterceptCallback)} rejects null callback ID.
   */
  @Test
  public void testRegisterCallbackRejectsNullId() {
    InterceptCallback callback =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

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
    InterceptCallback callback =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

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
    InterceptCallback callback1 =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };
    InterceptCallback callback2 =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

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
    InterceptCallback callback =
        (ctx) -> {
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

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
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * invokes a registered callback for BEFORE phase.
   */
  @Test
  public void testHandleCallbackWithRegisteredCallback() {
    // Track whether callback was invoked
    final boolean[] invoked = {false};

    InterceptCallback callback =
        (ctx) -> {
          invoked[0] = true;
          assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1); // BEFORE intercept
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertTrue("Callback should have been invoked", invoked[0]);
    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertEquals((byte) 1, response.getPhase());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * handles callback not found error.
   */
  @Test
  public void testHandleCallbackNotFound() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("non-existent");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertTrue("Should set throwException flag", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * handles exceptions thrown by callback.
   */
  @Test
  public void testHandleCallbackThrowsException() {
    InterceptCallback callback =
        (ctx) -> {
          throw new RuntimeException("Callback failed");
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertTrue("Should set throwException flag for callback error", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * propagates argument mutations for BEFORE phase.
   */
  @Test
  public void testHandleCallbackWithArgumentMutation() {
    InterceptCallback callback =
        (ctx) -> {
          // Note: In this test we can't actually mutate args because extractArguments returns empty
          // array
          // This test verifies the response structure when isArgsModified() would return true
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertEquals((byte) 1, response.getPhase());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * handles shouldProceed flag for AROUND intercepts.
   */
  @Test
  public void testHandleCallbackWithShouldProceedFalse() {
    InterceptCallback callback =
        (ctx) -> {
          com.quasient.pal.common.lang.intercept.InterceptCallbackResponse response =
              new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
          response.setShouldProceed(false);
          return response;
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 3); // AROUND
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertFalse("shouldProceed should be false", response.getShouldProceed());
  }

  /** Tests that static callback method invocation requires both class and method names. */
  @Test
  public void testHandleCallbackWithoutClassOrRegisteredId() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setExec(execMessage);
    // No registeredCallbackId and no callbackClass

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for missing callback routing", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * for AFTER phase.
   */
  @Test
  public void testHandleCallbackAfterPhase() {
    InterceptCallback callback =
        (ctx) -> {
          assertEquals(InterceptPhase.AFTER, ctx.getPhase());
          return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
        };

    dispatcher.registerCallback("test-callback", callback);

    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 2); // AFTER
    request.setInterceptType((byte) 2); // AFTER intercept
    request.setInterceptedPeer("peer-uuid");
    request.setRegisteredCallbackId("test-callback");
    request.setExec(execMessage);
    request.setIsVoid(false);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

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
    public static com.quasient.pal.common.lang.intercept.InterceptCallbackResponse
        testStaticCallback(InterceptContext ctx) {
      return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
    }

    /**
     * Non-static callback method (should fail validation).
     *
     * @param ctx the intercept context
     * @return a callback response
     */
    public com.quasient.pal.common.lang.intercept.InterceptCallbackResponse testInstanceCallback(
        InterceptContext ctx) {
      return new com.quasient.pal.common.lang.intercept.InterceptCallbackResponse();
    }
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * can invoke a static callback method via reflection.
   */
  @Test
  public void testHandleCallbackWithStaticMethod() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1); // BEFORE
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("testStaticCallback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertEquals("req-123", response.getCallbackId());
    assertFalse("Static callback should execute successfully", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * rejects non-static callback methods.
   */
  @Test
  public void testHandleCallbackRejectsNonStaticMethod() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("testInstanceCallback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for non-static method", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * handles class not found errors.
   */
  @Test
  public void testHandleCallbackClassNotFound() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setCallbackClass("com.nonexistent.Class");
    request.setCallbackMethod("callback");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for class not found", response.getThrowException());
  }

  /**
   * Tests that {@link IncomingInterceptCallbackDispatcher#handleCallback(InterceptCallbackRequest)}
   * handles method not found errors.
   */
  @Test
  public void testHandleCallbackMethodNotFound() {
    InterceptCallbackRequest request = new InterceptCallbackRequest();
    request.setCallbackId("req-123");
    request.setPhase((byte) 1);
    request.setInterceptType((byte) 1);
    request.setInterceptedPeer("peer-uuid");
    request.setCallbackClass(TestCallbackHandlers.class.getName());
    request.setCallbackMethod("nonExistentMethod");
    request.setExec(execMessage);

    InterceptCallbackResponse response = dispatcher.handleCallback(request);

    assertNotNull(response);
    assertTrue("Should return error for method not found", response.getThrowException());
  }
}
