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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import com.quasient.pal.core.intercept.InterceptCallbackDispatcher.AroundCallbackState;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher.AroundConsolidatedResponse;
import com.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link AroundConsolidatedResponse}.
 *
 * <p>Verifies the response container for AROUND BEFORE phase callbacks.
 */
public class AroundConsolidatedResponseTest {

  /** Tests the proceed() factory method returns a default proceed response. */
  @Test
  public void proceed_returnsDefaultProceedResponse() {
    AroundConsolidatedResponse response = AroundConsolidatedResponse.proceed();

    assertThat(response.shouldProceed(), is(true));
    assertThat(response.hasArgMutations(), is(false));
    assertThat(response.getMutatedArgs(), is(anEmptyMap()));
    assertThat(response.shouldThrowException(), is(false));
    assertThat(response.getExceptionToThrow(), is(nullValue()));
    assertThat(response.getSkipReturnValue(), is(nullValue()));
    assertThat(response.getPendingCallbacks(), is(empty()));
  }

  /** Tests the skipWithReturn() factory method with null return value. */
  @Test
  public void skipWithReturn_withNullReturnValue() {
    AroundConsolidatedResponse response = AroundConsolidatedResponse.skipWithReturn(null, null);

    assertThat(response.shouldProceed(), is(false));
    assertThat(response.getSkipReturnValue(), is(nullValue()));
    assertThat(response.shouldThrowException(), is(false));
    assertThat(response.getPendingCallbacks(), is(empty()));
  }

  /** Tests the skipWithReturn() factory method with return value. */
  @Test
  public void skipWithReturn_withReturnValue() {
    Object cachedValue = "cached result";
    AroundConsolidatedResponse response =
        AroundConsolidatedResponse.skipWithReturn(cachedValue, null);

    assertThat(response.shouldProceed(), is(false));
    assertThat(response.getSkipReturnValue(), is("cached result"));
    assertThat(response.shouldThrowException(), is(false));
  }

  /** Tests the skipWithReturn() factory method with exception. */
  @Test
  public void skipWithReturn_withException() {
    RuntimeException exception = new RuntimeException("validation failed");
    AroundConsolidatedResponse response =
        AroundConsolidatedResponse.skipWithReturn(null, exception);

    assertThat(response.shouldProceed(), is(false));
    assertThat(response.getSkipReturnValue(), is(nullValue()));
    assertThat(response.shouldThrowException(), is(true));
    assertThat(response.getExceptionToThrow(), is(sameInstance(exception)));
  }

  /** Tests constructor with all parameters for proceed scenario. */
  @Test
  public void constructor_proceedWithMutationsAndPendingCallbacks() {
    Map<Integer, Object> mutatedArgs = new HashMap<>();
    mutatedArgs.put(0, "mutated value");
    mutatedArgs.put(1, 42);

    InterceptMessage intercept = new InterceptMessage();
    UUID callbackPeer = UUID.randomUUID();
    AroundCallbackState state = new AroundCallbackState(intercept, "callback-123", callbackPeer);
    List<AroundCallbackState> pendingCallbacks = new ArrayList<>();
    pendingCallbacks.add(state);

    AroundConsolidatedResponse response =
        new AroundConsolidatedResponse(true, mutatedArgs, null, pendingCallbacks);

    assertThat(response.shouldProceed(), is(true));
    assertThat(response.hasArgMutations(), is(true));
    assertThat(response.getMutatedArgs().size(), is(2));
    assertThat(response.getMutatedArgs().get(0), is("mutated value"));
    assertThat(response.getMutatedArgs().get(1), is(42));
    assertThat(response.shouldThrowException(), is(false));
    assertThat(response.getPendingCallbacks().size(), is(1));
    assertThat(response.getPendingCallbacks().get(0).callbackId(), is("callback-123"));
  }

  /** Tests constructor with exception for proceed scenario. */
  @Test
  public void constructor_proceedWithException() {
    RuntimeException exception = new RuntimeException("callback error");
    Map<Integer, Object> mutatedArgs = new HashMap<>();
    List<AroundCallbackState> pendingCallbacks = new ArrayList<>();

    AroundConsolidatedResponse response =
        new AroundConsolidatedResponse(true, mutatedArgs, exception, pendingCallbacks);

    assertThat(response.shouldProceed(), is(true));
    assertThat(response.shouldThrowException(), is(true));
    assertThat(response.getExceptionToThrow(), is(sameInstance(exception)));
  }

  /** Tests hasArgMutations() with empty map. */
  @Test
  public void hasArgMutations_returnsFalseForEmptyMap() {
    Map<Integer, Object> emptyMutations = new HashMap<>();

    AroundConsolidatedResponse response =
        new AroundConsolidatedResponse(true, emptyMutations, null, new ArrayList<>());

    assertThat(response.hasArgMutations(), is(false));
  }

  /** Tests hasArgMutations() with non-empty map. */
  @Test
  public void hasArgMutations_returnsTrueForNonEmptyMap() {
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(0, "value");

    AroundConsolidatedResponse response =
        new AroundConsolidatedResponse(true, mutations, null, new ArrayList<>());

    assertThat(response.hasArgMutations(), is(true));
  }

  /** Tests AroundCallbackState record. */
  @Test
  public void aroundCallbackState_recordAccessors() {
    InterceptMessage intercept = new InterceptMessage();
    intercept.setClazz("com.example.Calculator");
    String callbackId = "cb-uuid-123";
    UUID callbackPeer = UUID.randomUUID();

    AroundCallbackState state = new AroundCallbackState(intercept, callbackId, callbackPeer);

    assertThat(state.intercept(), is(sameInstance(intercept)));
    assertThat(state.callbackId(), is("cb-uuid-123"));
    assertThat(state.callbackPeer(), is(callbackPeer));
  }

  /** Tests AroundCallbackState record equality. */
  @Test
  public void aroundCallbackState_equality() {
    InterceptMessage intercept = new InterceptMessage();
    UUID callbackPeer = UUID.randomUUID();

    AroundCallbackState state1 = new AroundCallbackState(intercept, "cb-1", callbackPeer);
    AroundCallbackState state2 = new AroundCallbackState(intercept, "cb-1", callbackPeer);
    AroundCallbackState state3 = new AroundCallbackState(intercept, "cb-2", callbackPeer);

    assertThat(state1.equals(state2), is(true));
    assertThat(state1.equals(state3), is(false));
  }

  /** Tests multiple pending callbacks. */
  @Test
  public void multiplePendingCallbacks() {
    InterceptMessage intercept1 = new InterceptMessage();
    InterceptMessage intercept2 = new InterceptMessage();
    UUID peer1 = UUID.randomUUID();
    UUID peer2 = UUID.randomUUID();

    List<AroundCallbackState> pendingCallbacks = new ArrayList<>();
    pendingCallbacks.add(new AroundCallbackState(intercept1, "cb-1", peer1));
    pendingCallbacks.add(new AroundCallbackState(intercept2, "cb-2", peer2));

    AroundConsolidatedResponse response =
        new AroundConsolidatedResponse(true, new HashMap<>(), null, pendingCallbacks);

    assertThat(response.getPendingCallbacks().size(), is(2));
    assertThat(response.getPendingCallbacks().get(0).callbackId(), is("cb-1"));
    assertThat(response.getPendingCallbacks().get(1).callbackId(), is("cb-2"));
  }
}
