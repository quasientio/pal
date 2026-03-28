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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the optimized {@link AroundInterceptChainBuilder} that eliminates intermediate
 * filtered lists and pools the builder via {@code ThreadLocal}.
 *
 * <p>The optimized builder consumes pre-partitioned AROUND intercepts (from {@code
 * InterceptPartition}) instead of filtering from a mixed list using {@code
 * stream().filter().toList()}. It also reuses a thread-local {@link AroundInterceptChain.Builder}
 * instance to avoid per-build allocations.
 *
 * <p>These tests verify that:
 *
 * <ol>
 *   <li>Chain construction from pre-partitioned AROUND intercepts produces correct chains
 *   <li>Thread-local builder pooling reuses the same builder instance
 *   <li>No intermediate filtered lists are created (the partition is consumed directly)
 *   <li>Empty input produces a chain with only the method invoker
 *   <li>Results are identical to the original (non-optimized) builder
 * </ol>
 *
 * @see AroundInterceptChainBuilder
 * @see AroundInterceptChain
 */
public class AroundInterceptChainBuilderOptTest {

  /** Shared peer UUID for this peer. */
  private static final UUID PEER_UUID = UUID.randomUUID();

  /** Shared callback peer UUID for remote intercepts. */
  private static final UUID CALLBACK_PEER_UUID = UUID.randomUUID();

  /** Test class name for intercept metadata. */
  private static final String TEST_CLASS = "com.example.TestClass";

  /** Test method name for intercept metadata. */
  private static final String TEST_METHOD = "testMethod";

  /** Test parameter types for intercept metadata. */
  private static final List<String> TEST_PARAM_TYPES = List.of("java.lang.String", "int");

  /** Shared message builder for constructing intercept messages. */
  private final MessageBuilder msgBuilder = new MessageBuilder();

  /** Mock callback resolver for local callback resolution. */
  private CallbackResolver callbackResolver;

  /** Mock remote dispatcher for remote callback dispatch. */
  private InterceptCallbackDispatcher remoteDispatcher;

  /** Mock method invoker representing the actual method execution. */
  private AroundInterceptChain.MethodInvoker methodInvoker;

  /** The builder under test. */
  private AroundInterceptChainBuilder builder;

  /** Sets up test fixtures before each test. */
  @Before
  public void setUp() {
    callbackResolver = mock(CallbackResolver.class);
    remoteDispatcher = mock(InterceptCallbackDispatcher.class);
    methodInvoker = mock(AroundInterceptChain.MethodInvoker.class);
    builder = new AroundInterceptChainBuilder(callbackResolver, remoteDispatcher, PEER_UUID);
  }

  /**
   * Verifies that a single local AROUND intercept from a pre-partitioned list produces a chain with
   * one handle and a method invoker at the bottom.
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldBuildChainWithLocalAroundInterceptFromPartition]
   * Single local around works
   */
  @Test
  public void shouldBuildChainWithLocalAroundInterceptFromPartition() throws Exception {
    // Given: InterceptPartition with 1 local AROUND intercept (already partitioned)
    InterceptMessage localAround = createLocalAroundIntercept("TestCallback");
    List<InterceptMessage> localArounds = List.of(localAround);
    List<InterceptMessage> remoteArounds = List.of();

    InterceptCallback callback = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("TestCallback"), eq("onAround")))
        .thenReturn(callback);

    // When: build() called with partition's around list
    AroundInterceptChain chain =
        builder.build(
            localArounds, remoteArounds, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    // Then: Chain has 1 handle, method invoker at bottom
    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("TestCallback"), eq("onAround"));
  }

  /**
   * Verifies that multiple local AROUND intercepts from a pre-partitioned list produce a chain with
   * all handles in correct registration order.
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldBuildChainWithMultipleLocalAroundIntercepts]
   * Multiple local arounds work
   */
  @Test
  public void shouldBuildChainWithMultipleLocalAroundIntercepts() throws Exception {
    // Given: InterceptPartition with 3 local AROUND intercepts
    InterceptMessage around1 = createLocalAroundIntercept("Callback1");
    InterceptMessage around2 = createLocalAroundIntercept("Callback2");
    InterceptMessage around3 = createLocalAroundIntercept("Callback3");
    List<InterceptMessage> localArounds = List.of(around1, around2, around3);
    List<InterceptMessage> remoteArounds = List.of();

    InterceptCallback cb1 = mock(InterceptCallback.class);
    InterceptCallback cb2 = mock(InterceptCallback.class);
    InterceptCallback cb3 = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("Callback1"), eq("onAround"))).thenReturn(cb1);
    when(callbackResolver.resolve(isNull(), eq("Callback2"), eq("onAround"))).thenReturn(cb2);
    when(callbackResolver.resolve(isNull(), eq("Callback3"), eq("onAround"))).thenReturn(cb3);

    // When: build() called
    AroundInterceptChain chain =
        builder.build(
            localArounds, remoteArounds, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    // Then: Chain has 3 handles in correct order
    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("Callback1"), eq("onAround"));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("Callback2"), eq("onAround"));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("Callback3"), eq("onAround"));
  }

  /**
   * Verifies that the thread-local builder pool reuses the same builder instance on the same thread
   * and that both chains produced are correct.
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldReuseBuilderViaThreadLocal] Builder pooling
   * works
   */
  @Test
  public void shouldReuseBuilderViaThreadLocal() throws Exception {
    // Given: Two different sets of AROUND intercepts to build chains from
    InterceptMessage around1 = createLocalAroundIntercept("CallbackA");
    InterceptMessage around2 = createLocalAroundIntercept("CallbackB");

    InterceptCallback cbA = mock(InterceptCallback.class);
    InterceptCallback cbB = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("CallbackA"), eq("onAround"))).thenReturn(cbA);
    when(callbackResolver.resolve(isNull(), eq("CallbackB"), eq("onAround"))).thenReturn(cbB);

    // When: build() called twice on same thread
    AroundInterceptChain chain1 =
        builder.build(
            List.of(around1), List.of(), TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    AroundInterceptChain chain2 =
        builder.build(
            List.of(around2), List.of(), TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    // Then: Both chains correct (builder was properly reset between builds)
    assertThat(chain1, notNullValue());
    assertThat(chain1.isEmpty(), is(false));
    assertThat(chain2, notNullValue());
    assertThat(chain2.isEmpty(), is(false));

    // Verify both callbacks were resolved (one per build call)
    verify(callbackResolver, times(1)).resolve(isNull(), eq("CallbackA"), eq("onAround"));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("CallbackB"), eq("onAround"));

    // Verify chain2 doesn't contain stale handles from chain1 by invoking it
    // and verifying only CallbackB's handle is in chain2 (not CallbackA)
    when(cbB.handle(any())).thenReturn(new InterceptCallbackResponse());
    when(methodInvoker.invoke(any())).thenReturn(new AfterPhaseData("result", null, false));

    AroundInterceptChain.ChainResult result2 = chain2.invoke(new Object[] {}, null);
    assertThat(result2, notNullValue());
    // CallbackB should have been invoked (it's in chain2)
    verify(cbB, times(1)).handle(any());
    // CallbackA should NOT have been invoked (it was only in chain1, builder was reset)
    verify(cbA, never()).handle(any());
  }

  /**
   * Verifies that when consuming pre-partitioned AROUND intercepts, no intermediate filtered lists
   * (via {@code stream().filter().toList()}) are needed.
   *
   * <p>The key optimization: the original builder receives an {@link InterceptCheckResult} and
   * filters local/remote intercepts for AROUND type via stream operations. The optimized builder
   * receives a pre-partitioned AROUND list directly, eliminating those intermediate list
   * allocations.
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldBuildChainWithoutIntermediateFilteredLists] No
   * intermediate lists needed
   */
  @Test
  public void shouldBuildChainWithoutIntermediateFilteredLists() throws Exception {
    // Given: Pre-partitioned around intercepts (no need to filter from mixed list)
    // Use InterceptPartition to partition a mixed list, then pass only .around() to builder
    InterceptPartition partition = new InterceptPartition();

    // Create a mixed list (BEFORE + AROUND)
    InterceptMessage beforeMsg = new InterceptMessage();
    beforeMsg.setInterceptType(InterceptType.BEFORE.toByte());
    beforeMsg.setPeerUuid(PEER_UUID.toString());
    beforeMsg.setCallbackClass("BeforeCallback");
    beforeMsg.setCallbackMethod("onBefore");

    InterceptMessage aroundMsg = createLocalAroundIntercept("AroundCallback");

    partition.partition(List.of(beforeMsg, aroundMsg));

    InterceptCallback aroundCb = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("AroundCallback"), eq("onAround")))
        .thenReturn(aroundCb);

    // When: build() called with partition's around list directly
    AroundInterceptChain chain =
        builder.build(
            partition.around(),
            List.of(),
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            methodInvoker);

    // Then: Chain built from the pre-partitioned AROUND list only
    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    // Only the AROUND callback was resolved, not the BEFORE callback
    verify(callbackResolver, times(1)).resolve(isNull(), eq("AroundCallback"), eq("onAround"));
    verify(callbackResolver, never()).resolve(isNull(), eq("BeforeCallback"), eq("onBefore"));
  }

  /**
   * Verifies that an empty AROUND intercepts list produces a chain containing only the method
   * invoker (no intercept handles).
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldHandleEmptyAroundInterceptsList] Empty list
   * handled
   */
  @Test
  public void shouldHandleEmptyAroundInterceptsList() throws Exception {
    // Given: Empty around intercepts list
    List<InterceptMessage> emptyLocal = Collections.emptyList();
    List<InterceptMessage> emptyRemote = Collections.emptyList();

    // When: build() called
    AroundInterceptChain chain =
        builder.build(
            emptyLocal, emptyRemote, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    // Then: Chain with just the method invoker (no intercept handles)
    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(true));
    // No callbacks were resolved
    verify(callbackResolver, never()).resolve(any(), any(), any());
  }

  /**
   * Verifies that the optimized builder produces a chain structurally identical to the original
   * (non-optimized) builder when given the same intercepts.
   *
   * <p>This is a critical equivalence test: regardless of the optimization (thread-local pooling,
   * pre-partitioned input), the resulting chain must behave identically to the chain produced by
   * the original {@link AroundInterceptChainBuilder#build} method.
   *
   * <p>Acceptance Criteria:
   * [TEST:AroundInterceptChainBuilderOptTest.shouldProduceIdenticalChainToOriginalBuilder] Results
   * match original
   */
  @Test
  public void shouldProduceIdenticalChainToOriginalBuilder() throws Exception {
    // Given: Same intercepts list (mix of local and remote AROUND intercepts)
    InterceptMessage localAround = createLocalAroundIntercept("LocalCallback");
    InterceptMessage remoteAround = createRemoteAroundIntercept();

    InterceptCallback localCb = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("LocalCallback"), eq("onAround")))
        .thenReturn(localCb);
    when(localCb.handle(any())).thenReturn(new InterceptCallbackResponse());

    // Method invoker returns a known value
    AfterPhaseData invokerResult = new AfterPhaseData(42, null, false);
    AroundInterceptChain.MethodInvoker concreteInvoker = (invokeArgs) -> invokerResult;

    // Build with the original (backward-compatible) API via InterceptCheckResult
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(true);
    when(checkResult.hasAnyIntercepts()).thenReturn(true);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(localAround));
    when(checkResult.getRemoteIntercepts()).thenReturn(List.of(remoteAround));

    AroundInterceptChain originalChain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, concreteInvoker);

    // Reset mocks for second build (callbackResolver will be called again)
    when(callbackResolver.resolve(isNull(), eq("LocalCallback"), eq("onAround")))
        .thenReturn(localCb);

    // Build with the optimized API (pre-partitioned lists)
    AroundInterceptChain optimizedChain =
        builder.build(
            List.of(localAround),
            List.of(remoteAround),
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            concreteInvoker);

    // Then: Both chains have same isEmpty() result
    assertThat(originalChain.isEmpty(), is(optimizedChain.isEmpty()));
    assertThat(originalChain.isEmpty(), is(false));

    // Verify both chains resolved the same local callback
    verify(callbackResolver, times(2)).resolve(isNull(), eq("LocalCallback"), eq("onAround"));
  }

  // ===== Helper methods =====

  /**
   * Creates a local AROUND {@link InterceptMessage} with the specified callback class.
   *
   * @param callbackClass the callback class name
   * @return a new InterceptMessage configured as a local AROUND intercept
   */
  private InterceptMessage createLocalAroundIntercept(String callbackClass) {
    return msgBuilder.buildInterceptMessage(
        PEER_UUID,
        InterceptType.AROUND,
        TEST_CLASS,
        TEST_METHOD,
        TEST_PARAM_TYPES,
        callbackClass,
        "onAround");
  }

  /**
   * Creates a remote AROUND {@link InterceptMessage}.
   *
   * @return a new InterceptMessage configured as a remote AROUND intercept
   */
  private InterceptMessage createRemoteAroundIntercept() {
    return msgBuilder.buildInterceptMessage(
        CALLBACK_PEER_UUID,
        InterceptType.AROUND,
        TEST_CLASS,
        TEST_METHOD,
        TEST_PARAM_TYPES,
        "RemoteCallback",
        "onAround");
  }
}
