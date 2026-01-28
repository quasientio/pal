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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/**
 * Unit tests for {@link RpcChainInstance} to ensure all convenience methods for RPC chain
 * operations work correctly.
 */
public class RpcChainInstanceTest {

  /** Creates a successful JSON-RPC response with the given ID. */
  private static JsonRpcResponse okResponse(String id) {
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue("result")
            .withType("java.lang.String")
            .withRef(1)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build();
    return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  }

  /**
   * Creates a mock peer that returns OK responses.
   *
   * @return a mock ThinPeer configured to return successful responses
   * @throws Exception if mock setup fails (required by method signature)
   */
  private ThinPeer mockPeer() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    when(peer.sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any()))
        .thenAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              return CompletableFuture.completedFuture(okResponse(req.getId()));
            });
    return peer;
  }

  // ==================== call() overload tests ====================

  /** Tests call(methodName) convenience overload. */
  @Test
  public void testCall_methodNameOnly() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.StringBuilder", "sb");
    RpcChainInstance result = inst.call("toString");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests call(methodName, args) convenience overload. */
  @Test
  public void testCall_methodNameWithArgs() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.StringBuilder", "sb");
    RpcChainInstance result = inst.call("append", new Object[] {"test"});
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests call(methodName, resultVarName) convenience overload. */
  @Test
  public void testCall_methodNameWithResultVar() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.StringBuilder", "sb");
    RpcChainInstance result = inst.call("toString", "str");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests call(methodName, resultVarName, args) with all parameters. */
  @Test
  public void testCall_allParams() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.StringBuilder", "sb");
    RpcChainInstance result = inst.call("indexOf", "idx", new Object[] {"test"});
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== get() overload tests ====================

  /** Tests get(fieldName) convenience overload. */
  @Test
  public void testGet_fieldNameOnly() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.get("CASE_INSENSITIVE_ORDER");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests get(fieldName, resultVarName) overload. */
  @Test
  public void testGet_fieldNameWithResultVar() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.get("CASE_INSENSITIVE_ORDER", "comparator");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== put() tests ====================

  /** Tests put(fieldName, value) method. */
  @Test
  public void testPut_fieldValue() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.Object", "obj");
    RpcChainInstance result = inst.put("someField", "value");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== static method delegation tests ====================

  /** Tests callStatic(className, methodName, args) delegation. */
  @Test
  public void testCallStatic_withArgs() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.callStatic("java.lang.Integer", "parseInt", new Object[] {"42"});
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests callStatic(className, methodName, resultVarName, args) delegation. */
  @Test
  public void testCallStatic_withResultVarAndArgs() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result =
        inst.callStatic("java.lang.Integer", "parseInt", "num", new Object[] {"42"});
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests getStatic(className, fieldName) delegation. */
  @Test
  public void testGetStatic_fieldOnly() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.getStatic("java.lang.Integer", "MAX_VALUE");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests getStatic(className, fieldName, resultVarName) delegation. */
  @Test
  public void testGetStatic_withResultVar() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.getStatic("java.lang.Integer", "MAX_VALUE", "maxInt");
    assertThat("Should return same instance for chaining", result, sameInstance(inst));

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== create() delegation tests ====================

  /** Tests create(className, varName, args) delegation. */
  @Test
  public void testCreate_withVarNameAndArgs() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.create("java.lang.StringBuilder", "sb2", new Object[] {"init"});
    assertThat("Should return new instance", result, notNullValue());

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests create(className, varName) delegation. */
  @Test
  public void testCreate_withVarName() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.create("java.lang.StringBuilder", "sb2");
    assertThat("Should return new instance", result, notNullValue());

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests create(className, args) delegation. */
  @Test
  public void testCreate_withArgs() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.create("java.lang.StringBuilder", new Object[] {"init"});
    assertThat("Should return new instance", result, notNullValue());

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  /** Tests create(className) delegation. */
  @Test
  public void testCreate_classNameOnly() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance result = inst.create("java.lang.StringBuilder");
    assertThat("Should return new instance", result, notNullValue());

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== with() delegation tests ====================

  /** Tests with(varName) delegation. */
  @Test
  public void testWith_delegation() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainInstance inst = chain.create("java.lang.StringBuilder", "sb", new Object[] {"init"});
    RpcChainInstance result = inst.with("s");
    assertThat("Should return instance for the var", result, notNullValue());

    RpcChainResult chainResult = chain.send();
    assertThat("Chain should execute", chainResult, notNullValue());
  }

  // ==================== send() delegation tests ====================

  /** Tests send() returns valid result. */
  @Test
  public void testSend_delegation() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"test"});
    RpcChainResult result = inst.send();
    assertThat("Should return result", result, notNullValue());
    assertThat("getAllValues should not be null", result.getAllValues(), notNullValue());
  }

  /** Tests chaining multiple operations then sending. */
  @Test
  public void testChainedOperations_thenSend() throws Exception {
    RpcChain chain = new RpcChain(mockPeer());
    RpcChainResult result =
        chain
            .create("java.lang.StringBuilder", "sb", new Object[] {"hello"})
            .call("append", new Object[] {" "})
            .call("append", new Object[] {"world"})
            .call("toString", "greeting")
            .getStatic("java.lang.Integer", "MAX_VALUE", "max")
            .send();

    assertThat("Should return result", result, notNullValue());
    assertThat(
        "Result toString should have info", result.toString(), containsString("chainValues"));
  }
}
