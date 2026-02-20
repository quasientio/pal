/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/**
 * Tests for thread affinity support in {@link RpcChain} and {@link RpcChainInstance}.
 *
 * <p>Verifies that {@code onFxThread()} and {@code withThreadAffinity()} correctly propagate the
 * thread affinity value to the {@code JsonRpcRequest} params sent to the peer.
 *
 * <p>Pattern: mock ThinPeer via Mockito, capture sent {@code JsonRpcRequest}s, and assert on {@code
 * request.getParams().getThreadAffinity()}.
 */
public class RpcChainThreadAffinityTest {

  /**
   * Builds a successful response with a ref and value suitable for constructor and method calls.
   *
   * @param id the request id to echo in the response
   * @return a completed JsonRpcResponse
   */
  private static JsonRpcResponse okResponseWithRef(String id) {
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue("42")
            .withType("java.lang.Integer")
            .withRef(1)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build();
    return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  }

  /**
   * Creates a mock ThinPeer that captures all sent JsonRpcRequests into the provided list.
   *
   * @param captured the list to capture requests into
   * @return a mocked ThinPeer
   */
  private static ThinPeer mockPeerCapturing(List<JsonRpcRequest> captured) throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    doAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              captured.add(req);
              return CompletableFuture.completedFuture(okResponseWithRef(req.getId()));
            })
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());
    return peer;
  }

  /**
   * Verifies that {@code onFxThread()} sets thread affinity to "fx-thread" on the next operation.
   *
   * <p>Acceptance criterion:
   * [TEST:RpcChainThreadAffinityTest.onFxThreadSetsThreadAffinityOnNextOperation]
   */
  @Test
  public void onFxThreadSetsThreadAffinityOnNextOperation() throws Exception {
    List<JsonRpcRequest> captured = new ArrayList<>();
    ThinPeer peer = mockPeerCapturing(captured);
    RpcChain chain = new RpcChain(peer);

    chain.onFxThread().callStatic("Foo", "bar").send();

    assertThat(captured.size(), is(1));
    assertThat(captured.get(0).getParams().getThreadAffinity(), is("fx-thread"));
  }

  /**
   * Verifies that {@code withThreadAffinity()} sets a custom thread affinity value.
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.withThreadAffinitySetsCustomValue]
   */
  @Test
  public void withThreadAffinitySetsCustomValue() throws Exception {
    List<JsonRpcRequest> captured = new ArrayList<>();
    ThinPeer peer = mockPeerCapturing(captured);
    RpcChain chain = new RpcChain(peer);

    chain.withThreadAffinity("custom-thread").callStatic("Foo", "bar").send();

    assertThat(captured.size(), is(1));
    assertThat(captured.get(0).getParams().getThreadAffinity(), is("custom-thread"));
  }

  /**
   * Verifies that thread affinity is {@code null} by default (no affinity modifier called).
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.threadAffinityNotSetByDefault]
   */
  @Test
  public void threadAffinityNotSetByDefault() throws Exception {
    List<JsonRpcRequest> captured = new ArrayList<>();
    ThinPeer peer = mockPeerCapturing(captured);
    RpcChain chain = new RpcChain(peer);

    chain.callStatic("Foo", "bar").send();

    assertThat(captured.size(), is(1));
    assertThat(captured.get(0).getParams().getThreadAffinity(), is(nullValue()));
  }

  /**
   * Verifies that thread affinity is consumed after a single operation — the second operation in
   * the chain should have {@code null} thread affinity.
   *
   * <p>Acceptance criterion:
   * [TEST:RpcChainThreadAffinityTest.threadAffinityConsumedAfterSingleOperation]
   */
  @Test
  public void threadAffinityConsumedAfterSingleOperation() throws Exception {
    List<JsonRpcRequest> captured = new ArrayList<>();
    ThinPeer peer = mockPeerCapturing(captured);
    RpcChain chain = new RpcChain(peer);

    chain.onFxThread().callStatic("Foo", "bar").callStatic("Foo", "baz").send();

    assertThat(captured.size(), is(2));
    assertThat(captured.get(0).getParams().getThreadAffinity(), is("fx-thread"));
    assertThat(captured.get(1).getParams().getThreadAffinity(), is(nullValue()));
  }

  /**
   * Verifies that {@code onFxThread()} on an {@link RpcChainInstance} sets thread affinity on the
   * next instance method call.
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.instanceOnFxThreadSetsThreadAffinity]
   */
  @Test
  public void instanceOnFxThreadSetsThreadAffinity() throws Exception {
    List<JsonRpcRequest> captured = new ArrayList<>();
    ThinPeer peer = mockPeerCapturing(captured);
    RpcChain chain = new RpcChain(peer);

    RpcChainInstance inst = chain.create("com.example.Label", "label", RpcChain.args("text"));
    inst.onFxThread().call("setText", RpcChain.args("Hello")).send();

    // First captured request is the constructor (create); second is the instance method call
    assertThat(captured.size(), is(2));
    assertThat(captured.get(1).getParams().getThreadAffinity(), is("fx-thread"));
  }
}
