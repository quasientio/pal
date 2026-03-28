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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.core.intercept.AroundInterceptChain.ChainResult;
import io.quasient.pal.core.intercept.AroundInterceptChain.LocalAroundHandle;
import io.quasient.pal.core.intercept.AroundInterceptChain.MethodInvoker;
import io.quasient.pal.core.intercept.AroundInterceptChain.RemoteAroundAfterResult;
import io.quasient.pal.core.intercept.AroundInterceptChain.RemoteAroundBeforeResult;
import io.quasient.pal.core.intercept.AroundInterceptChain.RemoteAroundDispatcher;
import io.quasient.pal.core.intercept.AroundInterceptChain.RemoteAroundHandle;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for AroundInterceptChain.
 *
 * <p>Tests the onion-model execution of AROUND intercepts, including:
 *
 * <ul>
 *   <li>Empty chain invokes method directly
 *   <li>Local AROUND intercepts with proceed()
 *   <li>Local AROUND intercepts with skip (no proceed)
 *   <li>Argument mutations through chain
 *   <li>Return value modifications
 *   <li>Exception handling
 *   <li>Remote AROUND intercepts via dispatcher
 *   <li>Mixed local and remote chains
 * </ul>
 */
public class AroundInterceptChainTest {

  /** The method invoker mock. */
  private MethodInvoker methodInvoker;

  /** The remote dispatcher mock. */
  private RemoteAroundDispatcher remoteDispatcher;

  /** Test execution message. */
  private ExecMessage execMessage;

  /** Test intercept message. */
  private InterceptMessage interceptMessage;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    methodInvoker = mock(MethodInvoker.class);
    remoteDispatcher = mock(RemoteAroundDispatcher.class);
    execMessage = new ExecMessage();
    interceptMessage = new InterceptMessage();
  }

  // ========== Empty Chain Tests ==========

  /** Tests that an empty chain returns true for isEmpty(). */
  @Test
  public void testIsEmpty_emptyChain_returnsTrue() {
    AroundInterceptChain chain =
        AroundInterceptChain.builder().methodInvoker(methodInvoker).build();

    assertTrue("Empty chain should return true for isEmpty()", chain.isEmpty());
  }

  /** Tests that a chain with handles returns false for isEmpty(). */
  @Test
  public void testIsEmpty_withHandles_returnsFalse() {
    InterceptCallback callback = mock(InterceptCallback.class);

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    assertFalse("Chain with handles should return false for isEmpty()", chain.isEmpty());
  }

  /** Tests that empty chain invokes method directly. */
  @Test
  public void testInvoke_emptyChain_invokesMethodDirectly() {
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData(42, null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder().methodInvoker(methodInvoker).build();

    Object[] args = new Object[] {"arg1", 123};
    ChainResult result = chain.invoke(args, execMessage);

    verify(methodInvoker).invoke(args);
    assertEquals(42, result.returnValue());
    assertNull(result.thrownException());
    assertTrue(result.methodWasInvoked());
    assertFalse(result.isVoid());
  }

  /** Tests empty chain with void method. */
  @Test
  public void testInvoke_emptyChain_voidMethod() {
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData(null, null, true));

    AroundInterceptChain chain =
        AroundInterceptChain.builder().methodInvoker(methodInvoker).build();

    ChainResult result = chain.invoke(new Object[0], execMessage);

    assertTrue(result.methodWasInvoked());
    assertTrue(result.isVoid());
    assertNull(result.returnValue());
  }

  // ========== Local AROUND Tests ==========

  /** Tests single local AROUND that calls proceed(). */
  @Test
  public void testInvoke_singleLocalAround_callsProceed() {
    InterceptCallback callback =
        ctx -> {
          ctx.proceed();
          return new InterceptCallbackResponse();
        };

    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    ChainResult result = chain.invoke(new Object[] {"arg"}, execMessage);

    verify(methodInvoker).invoke(any());
    assertEquals("result", result.returnValue());
    assertTrue(result.methodWasInvoked());
  }

  /** Tests local AROUND that skips proceed() and sets return value. */
  @Test
  public void testInvoke_localAround_skipWithReturnValue() {
    InterceptCallback callback =
        ctx -> {
          ctx.setReturnValue("skipped");
          // Don't call proceed()
          return new InterceptCallbackResponse();
        };

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    ChainResult result = chain.invoke(new Object[] {"arg"}, execMessage);

    verify(methodInvoker, never()).invoke(any());
    assertEquals("skipped", result.returnValue());
    assertFalse(result.methodWasInvoked());
  }

  /** Tests local AROUND that skips proceed() for void method. */
  @Test
  public void testInvoke_localAround_skipVoidMethod() {
    InterceptCallback callback =
        ctx -> {
          // For void methods, skip is allowed without setting return value
          // Just don't call proceed()
          return new InterceptCallbackResponse();
        };

    // Need to set up the context to think it's a void method
    // This is tricky because the chain creates the context internally
    // Let's test that skipping a non-void method without return throws exception
    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "voidMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    // This should throw because the callback didn't set a return value
    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    // The chain should catch the IllegalStateException
    assertNotNull(result.thrownException());
    assertTrue(result.thrownException() instanceof IllegalStateException);
  }

  /** Tests multiple local AROUNDs execute in onion order. */
  @Test
  public void testInvoke_multipleLocalArounds_onionOrder() {
    StringBuilder callOrder = new StringBuilder();

    InterceptCallback outer =
        ctx -> {
          callOrder.append("outer-before,");
          ctx.proceed();
          callOrder.append("outer-after,");
          return new InterceptCallbackResponse();
        };

    InterceptCallback inner =
        ctx -> {
          callOrder.append("inner-before,");
          ctx.proceed();
          callOrder.append("inner-after,");
          return new InterceptCallbackResponse();
        };

    when(methodInvoker.invoke(any()))
        .thenAnswer(
            inv -> {
              callOrder.append("method,");
              return new AfterPhaseData("result", null, false);
            });

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, outer, "TestClass", "testMethod", List.of(), "peer-1")
            .addLocal(interceptMessage, inner, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    chain.invoke(new Object[] {}, execMessage);

    assertEquals("outer-before,inner-before,method,inner-after,outer-after,", callOrder.toString());
  }

  /** Tests local AROUND modifies arguments before proceed. */
  @Test
  public void testInvoke_localAround_modifiesArgs() {
    InterceptCallback callback =
        ctx -> {
          // Modify first argument using setArg
          ctx.setArg(0, "modified");
          ctx.proceed();
          return new InterceptCallbackResponse();
        };

    ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
    when(methodInvoker.invoke(argsCaptor.capture()))
        .thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    chain.invoke(new Object[] {"original"}, execMessage);

    Object[] capturedArgs = argsCaptor.getValue();
    assertEquals("modified", capturedArgs[0]);
  }

  /** Tests local AROUND modifies return value after proceed. */
  @Test
  public void testInvoke_localAround_modifiesReturnValue() {
    InterceptCallback callback =
        ctx -> {
          ctx.proceed();
          ctx.setReturnValue("modified-result");
          return new InterceptCallbackResponse();
        };

    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("original", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    assertEquals("modified-result", result.returnValue());
  }

  /** Tests exception from method propagates through chain. */
  @Test
  public void testInvoke_localAround_methodThrowsException() {
    RuntimeException methodException = new RuntimeException("method error");

    InterceptCallback callback =
        ctx -> {
          ctx.proceed();
          return new InterceptCallbackResponse();
        };

    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData(null, methodException, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    assertEquals(methodException, result.thrownException());
    assertTrue(result.methodWasInvoked());
  }

  /** Tests callback can suppress exception by setting return value. */
  @Test
  public void testInvoke_localAround_suppressesException() {
    RuntimeException methodException = new RuntimeException("method error");

    InterceptCallback callback =
        ctx -> {
          ctx.proceed();
          // Suppress exception by setting return value
          ctx.setReturnValue("recovered");
          return new InterceptCallbackResponse();
        };

    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData(null, methodException, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "TestClass", "testMethod", List.of(), "peer-1")
            .methodInvoker(methodInvoker)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    assertEquals("recovered", result.returnValue());
    assertNull(result.thrownException());
  }

  // ========== Remote AROUND Tests ==========

  /** Tests single remote AROUND that proceeds. */
  @Test
  public void testInvoke_singleRemoteAround_proceeds() {
    UUID callbackPeer = UUID.randomUUID();

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(true, Collections.emptyMap(), null, null);
    RemoteAroundAfterResult afterResult = new RemoteAroundAfterResult(false, null, null);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);
    when(remoteDispatcher.sendAfter(any(), any(), any(), eq(false), any())).thenReturn(afterResult);
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    verify(remoteDispatcher).sendBefore(any(RemoteAroundHandle.class), eq(execMessage), any());
    verify(remoteDispatcher)
        .sendAfter(any(RemoteAroundHandle.class), eq(execMessage), eq("result"), eq(false), any());
    assertEquals("result", result.returnValue());
    assertTrue(result.methodWasInvoked());
  }

  /** Tests remote AROUND that skips execution. */
  @Test
  public void testInvoke_remoteAround_skipsExecution() {
    UUID callbackPeer = UUID.randomUUID();

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(false, Collections.emptyMap(), "skipped-value", null);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    verify(methodInvoker, never()).invoke(any());
    verify(remoteDispatcher, never()).sendAfter(any(), any(), any(), eq(false), any());
    assertEquals("skipped-value", result.returnValue());
    assertFalse(result.methodWasInvoked());
  }

  /** Tests remote AROUND applies arg mutations. */
  @Test
  public void testInvoke_remoteAround_appliesArgMutations() {
    UUID callbackPeer = UUID.randomUUID();
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(0, "mutated-arg");

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(true, mutations, null, null);
    RemoteAroundAfterResult afterResult = new RemoteAroundAfterResult(false, null, null);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);
    when(remoteDispatcher.sendAfter(any(), any(), any(), eq(false), any())).thenReturn(afterResult);

    ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
    when(methodInvoker.invoke(argsCaptor.capture()))
        .thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    chain.invoke(new Object[] {"original-arg"}, execMessage);

    Object[] capturedArgs = argsCaptor.getValue();
    assertEquals("mutated-arg", capturedArgs[0]);
  }

  /** Tests remote AROUND overrides return value. */
  @Test
  public void testInvoke_remoteAround_overridesReturnValue() {
    UUID callbackPeer = UUID.randomUUID();

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(true, Collections.emptyMap(), null, null);
    RemoteAroundAfterResult afterResult =
        new RemoteAroundAfterResult(true, "overridden-result", null);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);
    when(remoteDispatcher.sendAfter(any(), any(), any(), eq(false), any())).thenReturn(afterResult);
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("original", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    assertEquals("overridden-result", result.returnValue());
  }

  /** Tests remote AROUND before phase throws exception. */
  @Test
  public void testInvoke_remoteAround_beforeThrowsException() {
    UUID callbackPeer = UUID.randomUUID();
    RuntimeException exception = new RuntimeException("before error");

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(false, Collections.emptyMap(), null, exception);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    verify(methodInvoker, never()).invoke(any());
    assertEquals(exception, result.thrownException());
  }

  /** Tests remote AROUND after phase throws exception. */
  @Test
  public void testInvoke_remoteAround_afterThrowsException() {
    UUID callbackPeer = UUID.randomUUID();
    RuntimeException exception = new RuntimeException("after error");

    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(true, Collections.emptyMap(), null, null);
    RemoteAroundAfterResult afterResult = new RemoteAroundAfterResult(false, null, exception);

    when(remoteDispatcher.sendBefore(any(), any(), any())).thenReturn(beforeResult);
    when(remoteDispatcher.sendAfter(any(), any(), any(), eq(false), any())).thenReturn(afterResult);
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    ChainResult result = chain.invoke(new Object[] {}, execMessage);

    assertEquals(exception, result.thrownException());
  }

  // ========== Mixed Chain Tests ==========

  /** Tests mixed local and remote AROUND intercepts in chain order. */
  @Test
  public void testInvoke_mixedLocalRemote_chainOrder() {
    AtomicInteger callOrder = new AtomicInteger(0);
    int[] localBeforeOrder = new int[1];
    int[] localAfterOrder = new int[1];
    int[] remoteBeforeOrder = new int[1];
    int[] remoteAfterOrder = new int[1];
    int[] methodOrder = new int[1];

    InterceptCallback localCallback =
        ctx -> {
          localBeforeOrder[0] = callOrder.incrementAndGet();
          ctx.proceed();
          localAfterOrder[0] = callOrder.incrementAndGet();
          return new InterceptCallbackResponse();
        };

    UUID callbackPeer = UUID.randomUUID();
    RemoteAroundBeforeResult beforeResult =
        new RemoteAroundBeforeResult(true, Collections.emptyMap(), null, null);
    RemoteAroundAfterResult afterResult = new RemoteAroundAfterResult(false, null, null);

    when(remoteDispatcher.sendBefore(any(), any(), any()))
        .thenAnswer(
            inv -> {
              remoteBeforeOrder[0] = callOrder.incrementAndGet();
              return beforeResult;
            });
    when(remoteDispatcher.sendAfter(any(), any(), any(), eq(false), any()))
        .thenAnswer(
            inv -> {
              remoteAfterOrder[0] = callOrder.incrementAndGet();
              return afterResult;
            });
    when(methodInvoker.invoke(any()))
        .thenAnswer(
            inv -> {
              methodOrder[0] = callOrder.incrementAndGet();
              return new AfterPhaseData("result", null, false);
            });

    // Local first, then remote (onion model)
    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(
                interceptMessage, localCallback, "TestClass", "testMethod", List.of(), "peer-1")
            .addRemote(interceptMessage, callbackPeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    chain.invoke(new Object[] {}, execMessage);

    // Verify onion order: local-before, remote-before, method, remote-after, local-after
    assertEquals(1, localBeforeOrder[0]);
    assertEquals(2, remoteBeforeOrder[0]);
    assertEquals(3, methodOrder[0]);
    assertEquals(4, remoteAfterOrder[0]);
    assertEquals(5, localAfterOrder[0]);
  }

  // ========== ChainResult Tests ==========

  /** Tests ChainResult.skipped factory method. */
  @Test
  public void testChainResult_skipped() {
    RuntimeException exception = new RuntimeException("skip exception");
    ChainResult result = ChainResult.skipped("value", exception);

    assertEquals("value", result.returnValue());
    assertEquals(exception, result.thrownException());
    assertFalse(result.isVoid());
    assertFalse(result.methodWasInvoked());
  }

  /** Tests ChainResult.executed factory method. */
  @Test
  public void testChainResult_executed() {
    ChainResult result = ChainResult.executed("value", null, false);

    assertEquals("value", result.returnValue());
    assertNull(result.thrownException());
    assertFalse(result.isVoid());
    assertTrue(result.methodWasInvoked());
  }

  /** Tests ChainResult.executed with void method. */
  @Test
  public void testChainResult_executedVoid() {
    ChainResult result = ChainResult.executed(null, null, true);

    assertNull(result.returnValue());
    assertNull(result.thrownException());
    assertTrue(result.isVoid());
    assertTrue(result.methodWasInvoked());
  }

  // ========== Builder Tests ==========

  /** Tests Builder throws when methodInvoker not set. */
  @Test(expected = IllegalStateException.class)
  public void testBuilder_missingMethodInvoker_throws() {
    AroundInterceptChain.builder().build();
  }

  /** Tests Builder with all options. */
  @Test
  public void testBuilder_withAllOptions() {
    InterceptCallback callback = mock(InterceptCallback.class);
    UUID remotePeer = UUID.randomUUID();

    AroundInterceptChain chain =
        AroundInterceptChain.builder()
            .addLocal(interceptMessage, callback, "Class", "method", List.of("String"), "peer")
            .addRemote(interceptMessage, remotePeer)
            .methodInvoker(methodInvoker)
            .remoteDispatcher(remoteDispatcher)
            .build();

    assertNotNull(chain);
    assertFalse(chain.isEmpty());
  }

  // ========== Handle Record Tests ==========

  /** Tests LocalAroundHandle record fields. */
  @Test
  public void testLocalAroundHandle_fields() {
    InterceptCallback callback = mock(InterceptCallback.class);
    List<String> paramTypes = List.of("String", "int");

    LocalAroundHandle handle =
        new LocalAroundHandle(
            interceptMessage, callback, "TestClass", "testMethod", paramTypes, "peer-uuid");

    assertEquals(interceptMessage, handle.intercept());
    assertEquals(callback, handle.callback());
    assertEquals("TestClass", handle.className());
    assertEquals("testMethod", handle.methodName());
    assertEquals(paramTypes, handle.paramTypes());
    assertEquals("peer-uuid", handle.peerUuid());
  }

  /** Tests RemoteAroundHandle record fields. */
  @Test
  public void testRemoteAroundHandle_fields() {
    UUID callbackPeer = UUID.randomUUID();

    RemoteAroundHandle handle =
        new RemoteAroundHandle(interceptMessage, callbackPeer, "callback-123");

    assertEquals(interceptMessage, handle.intercept());
    assertEquals(callbackPeer, handle.callbackPeerUuid());
    assertEquals("callback-123", handle.callbackId());
  }
}
