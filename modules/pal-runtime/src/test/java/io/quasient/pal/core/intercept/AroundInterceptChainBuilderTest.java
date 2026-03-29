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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link AroundInterceptChainBuilder}.
 *
 * <p>These tests verify the chain building logic for AROUND intercepts, including both local and
 * remote callback handling.
 */
public class AroundInterceptChainBuilderTest {

  private static final UUID PEER_UUID = UUID.randomUUID();
  private static final UUID CALLBACK_PEER_UUID = UUID.randomUUID();
  private static final String TEST_CLASS = "com.example.TestClass";
  private static final String TEST_METHOD = "testMethod";
  private static final List<String> TEST_PARAM_TYPES = List.of("java.lang.String", "int");

  private CallbackResolver callbackResolver;
  private InterceptCallbackDispatcher remoteDispatcher;
  private AroundInterceptChainBuilder builder;
  private AroundInterceptChain.MethodInvoker methodInvoker;

  @Before
  public void setUp() {
    callbackResolver = mock(CallbackResolver.class);
    remoteDispatcher = mock(InterceptCallbackDispatcher.class);
    methodInvoker = mock(AroundInterceptChain.MethodInvoker.class);
    builder = new AroundInterceptChainBuilder(callbackResolver, remoteDispatcher, PEER_UUID);
  }

  // ===== Tests for empty/null intercepts =====

  /** Tests that building with no intercepts returns an empty chain. */
  @Test
  public void build_noIntercepts_returnsEmptyChain() {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(false);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(true));
  }

  /** Tests that building with non-AROUND local intercepts returns an empty chain. */
  @Test
  public void build_onlyBeforeIntercepts_returnsEmptyChain() {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    InterceptMessage beforeMessage = createInterceptMessage(InterceptType.BEFORE);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(beforeMessage));

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(true));
  }

  // ===== Tests for local AROUND intercepts =====

  /** Tests that a single local AROUND intercept is added to the chain. */
  @Test
  public void build_singleLocalAround_addsToChain() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    InterceptMessage aroundMessage = createInterceptMessage(InterceptType.AROUND);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(aroundMessage));

    InterceptCallback callback = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("TestCallback"), eq("onAround")))
        .thenReturn(callback);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("TestCallback"), eq("onAround"));
  }

  /** Tests that multiple local AROUND intercepts are all added to the chain. */
  @Test
  public void build_multipleLocalArounds_addsAllToChain() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    InterceptMessage aroundMessage1 = createInterceptMessage(InterceptType.AROUND, "Callback1");
    InterceptMessage aroundMessage2 = createInterceptMessage(InterceptType.AROUND, "Callback2");
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(aroundMessage1, aroundMessage2));

    InterceptCallback callback1 = mock(InterceptCallback.class);
    InterceptCallback callback2 = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("Callback1"), eq("onAround"))).thenReturn(callback1);
    when(callbackResolver.resolve(isNull(), eq("Callback2"), eq("onAround"))).thenReturn(callback2);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("Callback1"), eq("onAround"));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("Callback2"), eq("onAround"));
  }

  /** Tests that callback resolution failure is handled gracefully. */
  @Test
  public void build_callbackResolutionFails_continuesBuilding() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    InterceptMessage aroundMessage = createInterceptMessage(InterceptType.AROUND);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(aroundMessage));

    when(callbackResolver.resolve(any(), any(), any()))
        .thenThrow(new RuntimeException("Resolution failed"));

    // Should not throw, but log an error and continue
    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    // Chain is empty because the callback resolution failed
    assertThat(chain.isEmpty(), is(true));
  }

  // ===== Tests for remote AROUND intercepts =====

  /** Tests that a single remote AROUND intercept is added to the chain. */
  @Test
  public void build_singleRemoteAround_addsToChain() {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(false);
    when(checkResult.hasRemoteIntercepts()).thenReturn(true);

    InterceptMessage aroundMessage = createRemoteInterceptMessage(InterceptType.AROUND);
    when(checkResult.getRemoteIntercepts()).thenReturn(List.of(aroundMessage));

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
  }

  /** Tests that multiple remote AROUND intercepts are all added to the chain. */
  @Test
  public void build_multipleRemoteArounds_addsAllToChain() {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(false);
    when(checkResult.hasRemoteIntercepts()).thenReturn(true);

    InterceptMessage aroundMessage1 = createRemoteInterceptMessage(InterceptType.AROUND);
    InterceptMessage aroundMessage2 = createRemoteInterceptMessage(InterceptType.AROUND);
    when(checkResult.getRemoteIntercepts()).thenReturn(List.of(aroundMessage1, aroundMessage2));

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
  }

  // ===== Tests for mixed local and remote AROUND intercepts =====

  /** Tests that both local and remote AROUND intercepts are added to the chain. */
  @Test
  public void build_mixedLocalAndRemoteArounds_addsAllToChain() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(true);

    InterceptMessage localAround = createInterceptMessage(InterceptType.AROUND);
    InterceptMessage remoteAround = createRemoteInterceptMessage(InterceptType.AROUND);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(localAround));
    when(checkResult.getRemoteIntercepts()).thenReturn(List.of(remoteAround));

    InterceptCallback callback = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("TestCallback"), eq("onAround")))
        .thenReturn(callback);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    verify(callbackResolver, times(1)).resolve(isNull(), eq("TestCallback"), eq("onAround"));
  }

  /** Tests that AFTER intercepts are filtered out when building the AROUND chain. */
  @Test
  public void build_mixedAroundAndAfter_onlyAddsArounds() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(true);

    InterceptMessage localAround = createInterceptMessage(InterceptType.AROUND);
    InterceptMessage localAfter = createInterceptMessage(InterceptType.AFTER);
    InterceptMessage remoteAround = createRemoteInterceptMessage(InterceptType.AROUND);
    InterceptMessage remoteAfter = createRemoteInterceptMessage(InterceptType.AFTER);

    when(checkResult.getLocalIntercepts()).thenReturn(List.of(localAround, localAfter));
    when(checkResult.getRemoteIntercepts()).thenReturn(List.of(remoteAround, remoteAfter));

    InterceptCallback callback = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("TestCallback"), eq("onAround")))
        .thenReturn(callback);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
    // Only one local AROUND callback should be resolved (not the AFTER)
    verify(callbackResolver, times(1)).resolve(isNull(), eq("TestCallback"), eq("onAround"));
  }

  // ===== Tests for empty parameter types =====

  /** Tests building with empty parameter types. */
  @Test
  public void build_emptyParamTypes_buildsSuccessfully() throws Exception {
    InterceptCheckResult checkResult = mock(InterceptCheckResult.class);
    when(checkResult.hasLocalIntercepts()).thenReturn(true);
    when(checkResult.hasRemoteIntercepts()).thenReturn(false);

    InterceptMessage aroundMessage = createInterceptMessage(InterceptType.AROUND);
    when(checkResult.getLocalIntercepts()).thenReturn(List.of(aroundMessage));

    InterceptCallback callback = mock(InterceptCallback.class);
    when(callbackResolver.resolve(isNull(), eq("TestCallback"), eq("onAround")))
        .thenReturn(callback);

    AroundInterceptChain chain =
        builder.build(checkResult, TEST_CLASS, TEST_METHOD, Collections.emptyList(), methodInvoker);

    assertThat(chain, notNullValue());
    assertThat(chain.isEmpty(), is(false));
  }

  // ===== Helper methods =====

  /**
   * Creates a local intercept message with the given type.
   *
   * @param type the intercept type
   * @return the intercept message
   */
  private InterceptMessage createInterceptMessage(InterceptType type) {
    return createInterceptMessage(type, "TestCallback");
  }

  /**
   * Creates a local intercept message with the given type and callback class.
   *
   * @param type the intercept type
   * @param callbackClass the callback class name
   * @return the intercept message
   */
  private InterceptMessage createInterceptMessage(InterceptType type, String callbackClass) {
    InterceptMessage message = new InterceptMessage();
    message.setInterceptType(type.toByte());
    message.setCallbackClass(callbackClass);
    message.setCallbackMethod("onAround");
    message.setPeerUuid(UuidUtils.toBytes(PEER_UUID));
    return message;
  }

  /**
   * Creates a remote intercept message with the given type.
   *
   * @param type the intercept type
   * @return the intercept message
   */
  private InterceptMessage createRemoteInterceptMessage(InterceptType type) {
    InterceptMessage message = new InterceptMessage();
    message.setInterceptType(type.toByte());
    message.setCallbackClass("RemoteCallback");
    message.setCallbackMethod("onAround");
    message.setPeerUuid(UuidUtils.toBytes(CALLBACK_PEER_UUID));
    return message;
  }
}
