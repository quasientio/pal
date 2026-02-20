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

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for thread affinity support in {@link RpcChain} and {@link RpcChainInstance}.
 *
 * <p>Verifies that {@code onFxThread()} and {@code withThreadAffinity()} correctly propagate the
 * thread affinity value to the {@code JsonRpcRequest} params sent to the peer.
 *
 * <p>All tests are skipped until the RpcChain DSL thread affinity methods are implemented in #747.
 * The test bodies contain pseudo-code describing the expected behavior; once #747 adds {@code
 * onFxThread()}, {@code withThreadAffinity()}, and the {@code DeferredOperation.threadAffinity}
 * field, the pseudo-code should be replaced with real assertions.
 *
 * <p>Pattern: mock ThinPeer via Mockito, capture sent {@code JsonRpcRequest}s, and assert on {@code
 * request.getParams().getThreadAffinity()}.
 */
public class RpcChainThreadAffinityTest {

  // ---------------------------------------------------------------------------
  // Test helper pseudo-code (to be uncommented/implemented in #747):
  //
  //   private static JsonRpcResponse okResponseWithRef(String id) {
  //     ResponseObject ro = ResponseObject.builder()
  //         .withIsNull(false).withValue("42")
  //         .withType("java.lang.Integer").withRef(1).build();
  //     JsonRpcResponseReturnValue rv = JsonRpcResponseReturnValue.builder()
  //         .withIsVoid(false).withValue(ro).build();
  //     return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  //   }
  //
  //   private static ThinPeer mockPeerCapturing(List<JsonRpcRequest> captured) {
  //     ThinPeer peer = mock(ThinPeer.class);
  //     when(peer.sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any()))
  //         .thenAnswer(inv -> {
  //           JsonRpcRequest req = inv.getArgument(0);
  //           captured.add(req);
  //           return CompletableFuture.completedFuture(okResponseWithRef(req.getId()));
  //         });
  //     return peer;
  //   }
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code onFxThread()} sets thread affinity to "fx-thread" on the next operation.
   *
   * <p>Acceptance criterion:
   * [TEST:RpcChainThreadAffinityTest.onFxThreadSetsThreadAffinityOnNextOperation]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void onFxThreadSetsThreadAffinityOnNextOperation() {
    // Given: RpcChain with mock ThinPeer
    // When:  chain.onFxThread().callStatic("Foo", "bar").send()
    // Then:  JsonRpcRequest sent to ThinPeer has params.threadAffinity == "fx-thread"
    //
    // List<JsonRpcRequest> captured = new ArrayList<>();
    // ThinPeer peer = mockPeerCapturing(captured);
    // RpcChain chain = new RpcChain(peer);
    //
    // chain.onFxThread().callStatic("Foo", "bar").send();
    //
    // assertThat(captured.size(), is(1));
    // assertThat(captured.get(0).getParams().getThreadAffinity(), is("fx-thread"));

    fail("Not yet implemented — awaiting #747");
  }

  /**
   * Verifies that {@code withThreadAffinity()} sets a custom thread affinity value.
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.withThreadAffinitySetsCustomValue]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void withThreadAffinitySetsCustomValue() {
    // Given: RpcChain with mock ThinPeer
    // When:  chain.withThreadAffinity("custom-thread").callStatic("Foo", "bar").send()
    // Then:  JsonRpcRequest has params.threadAffinity == "custom-thread"
    //
    // List<JsonRpcRequest> captured = new ArrayList<>();
    // ThinPeer peer = mockPeerCapturing(captured);
    // RpcChain chain = new RpcChain(peer);
    //
    // chain.withThreadAffinity("custom-thread").callStatic("Foo", "bar").send();
    //
    // assertThat(captured.size(), is(1));
    // assertThat(captured.get(0).getParams().getThreadAffinity(), is("custom-thread"));

    fail("Not yet implemented — awaiting #747");
  }

  /**
   * Verifies that thread affinity is {@code null} by default (no affinity modifier called).
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.threadAffinityNotSetByDefault]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void threadAffinityNotSetByDefault() {
    // Given: RpcChain with mock ThinPeer
    // When:  chain.callStatic("Foo", "bar").send()
    // Then:  JsonRpcRequest has params.threadAffinity == null
    //
    // List<JsonRpcRequest> captured = new ArrayList<>();
    // ThinPeer peer = mockPeerCapturing(captured);
    // RpcChain chain = new RpcChain(peer);
    //
    // chain.callStatic("Foo", "bar").send();
    //
    // assertThat(captured.size(), is(1));
    // assertThat(captured.get(0).getParams().getThreadAffinity(), is(nullValue()));

    fail("Not yet implemented — awaiting #747");
  }

  /**
   * Verifies that thread affinity is consumed after a single operation — the second operation in
   * the chain should have {@code null} thread affinity.
   *
   * <p>Acceptance criterion:
   * [TEST:RpcChainThreadAffinityTest.threadAffinityConsumedAfterSingleOperation]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void threadAffinityConsumedAfterSingleOperation() {
    // Given: RpcChain with mock ThinPeer
    // When:  chain.onFxThread().callStatic("Foo", "bar").callStatic("Foo", "baz").send()
    // Then:  First request has threadAffinity == "fx-thread";
    //        second has threadAffinity == null (consumed after one operation)
    //
    // List<JsonRpcRequest> captured = new ArrayList<>();
    // ThinPeer peer = mockPeerCapturing(captured);
    // RpcChain chain = new RpcChain(peer);
    //
    // chain.onFxThread().callStatic("Foo", "bar").callStatic("Foo", "baz").send();
    //
    // assertThat(captured.size(), is(2));
    // assertThat(captured.get(0).getParams().getThreadAffinity(), is("fx-thread"));
    // assertThat(captured.get(1).getParams().getThreadAffinity(), is(nullValue()));

    fail("Not yet implemented — awaiting #747");
  }

  /**
   * Verifies that {@code onFxThread()} on an {@link RpcChainInstance} sets thread affinity on the
   * next instance method call.
   *
   * <p>Acceptance criterion: [TEST:RpcChainThreadAffinityTest.instanceOnFxThreadSetsThreadAffinity]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void instanceOnFxThreadSetsThreadAffinity() {
    // Given: RpcChainInstance with mock ThinPeer, instance created
    // When:  instance.onFxThread().call("setText", args("Hello")).send()
    // Then:  Instance method call has threadAffinity == "fx-thread"
    //
    // List<JsonRpcRequest> captured = new ArrayList<>();
    // ThinPeer peer = mockPeerCapturing(captured);
    // RpcChain chain = new RpcChain(peer);
    //
    // RpcChainInstance inst = chain.create("com.example.Label", "label", RpcChain.args("text"));
    // inst.onFxThread().call("setText", RpcChain.args("Hello")).send();
    //
    // // First captured request is the constructor (create); second is the instance method call
    // assertThat(captured.size(), is(2));
    // assertThat(captured.get(1).getParams().getThreadAffinity(), is("fx-thread"));

    fail("Not yet implemented — awaiting #747");
  }
}
