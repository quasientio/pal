/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.serdes.colfer.ExceptionSerdes;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for AROUND callback-related private methods in SocketRpcInvoker.
 *
 * <p>Tests the helper methods used during AROUND intercept callback processing.
 */
public class SocketRpcInvokerAroundCallbackTest {

  // ===== isAroundBeforePhase Tests =====

  /** Tests that AROUND BEFORE phase returns true. */
  @Test
  public void isAroundBeforePhase_aroundBefore_returnsTrue() throws Exception {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    req.setInterceptType(InterceptType.AROUND.toByte());
    req.setPhase(InterceptPhase.BEFORE.toByte());

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "isAroundBeforePhase", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    // Create minimal invoker for reflection call
    SocketRpcInvoker invoker = createMinimalInvoker();
    boolean result = (boolean) m.invoke(invoker, req);

    assertThat(result, is(true));
  }

  /** Tests that AROUND AFTER phase returns false. */
  @Test
  public void isAroundBeforePhase_aroundAfter_returnsFalse() throws Exception {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    req.setInterceptType(InterceptType.AROUND.toByte());
    req.setPhase(InterceptPhase.AFTER.toByte());

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "isAroundBeforePhase", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    boolean result = (boolean) m.invoke(invoker, req);

    assertThat(result, is(false));
  }

  /** Tests that BEFORE (not AROUND) returns false. */
  @Test
  public void isAroundBeforePhase_notAround_returnsFalse() throws Exception {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    req.setInterceptType(InterceptType.BEFORE.toByte());
    req.setPhase(InterceptPhase.BEFORE.toByte());

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "isAroundBeforePhase", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    boolean result = (boolean) m.invoke(invoker, req);

    assertThat(result, is(false));
  }

  // ===== buildErrorResponse Tests =====

  /** Tests building error response from exception. */
  @Test
  public void buildErrorResponse_withException_setsFields() throws Exception {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    req.setCallbackId("callback-123");
    req.setPhase(InterceptPhase.BEFORE.toByte());

    Exception error = new RuntimeException("Test error");

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "buildErrorResponse", InterceptCallbackRequestMessage.class, Exception.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    InterceptCallbackResponseMessage response =
        (InterceptCallbackResponseMessage) m.invoke(invoker, req, error);

    assertThat(response.getCallbackId(), is("callback-123"));
    assertThat(response.getPhase(), is(InterceptPhase.BEFORE.toByte()));
    assertThat(response.getThrowException(), is(true));
    assertThat(response.getException(), notNullValue());
  }

  /** Tests building error response preserves callback ID. */
  @Test
  public void buildErrorResponse_preservesCallbackId() throws Exception {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    req.setCallbackId("unique-callback-id");
    req.setPhase(InterceptPhase.AFTER.toByte());

    Exception error = new IllegalArgumentException("Bad argument");

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "buildErrorResponse", InterceptCallbackRequestMessage.class, Exception.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    InterceptCallbackResponseMessage response =
        (InterceptCallbackResponseMessage) m.invoke(invoker, req, error);

    assertThat(response.getCallbackId(), is("unique-callback-id"));
    assertThat(response.getPhase(), is(InterceptPhase.AFTER.toByte()));
  }

  // ===== parseAfterPhaseData Tests =====

  /** Tests parsing AFTER phase data with void return. */
  @Test
  public void parseAfterPhaseData_voidReturn_isVoidTrue() throws Exception {
    InterceptCallbackRequestMessage afterReq = new InterceptCallbackRequestMessage();
    afterReq.setIsVoid(true);
    afterReq.setReturnValue(null);
    afterReq.setThrownException(null);

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "parseAfterPhaseData", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    Object result = m.invoke(invoker, afterReq);

    assertThat(result, notNullValue());
    // AfterPhaseData has isVoid() method
    Method isVoidMethod = result.getClass().getMethod("isVoid");
    assertThat((boolean) isVoidMethod.invoke(result), is(true));
  }

  /** Tests parsing AFTER phase data with thrown exception. */
  @Test
  public void parseAfterPhaseData_withException_hasThrownException() throws Exception {
    InterceptCallbackRequestMessage afterReq = new InterceptCallbackRequestMessage();
    afterReq.setIsVoid(false);
    afterReq.setReturnValue(null);
    afterReq.setThrownException(
        ExceptionSerdes.serializeException(new RuntimeException("Test exception")));

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "parseAfterPhaseData", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    Object result = m.invoke(invoker, afterReq);

    assertThat(result, notNullValue());
    // AfterPhaseData has thrownException() method
    Method thrownMethod = result.getClass().getMethod("thrownException");
    Object thrown = thrownMethod.invoke(result);
    assertThat(thrown, notNullValue());
  }

  /** Tests parsing AFTER phase data with null exception. */
  @Test
  public void parseAfterPhaseData_noException_thrownExceptionNull() throws Exception {
    InterceptCallbackRequestMessage afterReq = new InterceptCallbackRequestMessage();
    afterReq.setIsVoid(false);
    afterReq.setReturnValue(null);
    afterReq.setThrownException(null);

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod(
            "parseAfterPhaseData", InterceptCallbackRequestMessage.class);
    m.setAccessible(true);

    SocketRpcInvoker invoker = createMinimalInvoker();
    Object result = m.invoke(invoker, afterReq);

    assertThat(result, notNullValue());
    Method thrownMethod = result.getClass().getMethod("thrownException");
    Object thrown = thrownMethod.invoke(result);
    assertThat(thrown == null, is(true));
  }

  /**
   * Creates a minimal SocketRpcInvoker for testing private methods.
   *
   * @return a minimal invoker instance
   */
  private SocketRpcInvoker createMinimalInvoker() {
    return new SocketRpcInvoker(
        null, // zmqContext - not needed for these tests
        null, // messageBuilder - not needed for these tests
        Collections.emptySet(),
        "inproc://rpc",
        "inproc://json",
        null, // dispatcher - not needed for these tests
        UUID.randomUUID());
  }
}
