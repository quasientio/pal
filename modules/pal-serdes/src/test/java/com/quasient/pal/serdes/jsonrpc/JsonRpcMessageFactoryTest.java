/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class JsonRpcMessageFactoryTest {

  @Test
  public void buildConstructorCallWithoutArgs() {
    JsonRpcRequest request = JsonRpcMessageFactory.buildConstructorCall("SomeClass", null);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("new"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildConstructorCall(createdId, "SomeClass", null);
    assertThat(request2, is(request));
  }

  @Test
  public void buildConstructorCallWithArgs() {
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request = JsonRpcMessageFactory.buildConstructorCall("SomeClass", args);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("new"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getArgs(), is(args));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildConstructorCall(createdId, "SomeClass", args);
    assertThat(request2, is(request));
  }

  @Test
  public void buildClassMethodCallWithoutArgs() {
    List<Argument> emptyArgs = new ArrayList<>();
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall("SomeClass", "someMethod", emptyArgs);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildClassMethodCall(createdId, "SomeClass", "someMethod", emptyArgs);
    assertThat(request2, is(request));
  }

  @Test
  public void buildClassMethodCallWithArgs() {
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall("SomeClass", "someMethod", args);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getInstance());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(args));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildClassMethodCall(createdId, "SomeClass", "someMethod", args);
    assertThat(request2, is(request));
  }

  @Test
  public void buildInstanceMethodCallWithoutArgs() {
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceMethodCall("SomeClass", "someMethod", instanceId, null);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            createdId, "SomeClass", "someMethod", instanceId, null);
    assertThat(request2, is(request));

    // create another request with instance as ObjectRef, and test equality
    JsonRpcRequest request3 =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            createdId, "SomeClass", "someMethod", ObjectRef.from(instanceId), null);
    assertThat(request3, is(request));
  }

  @Test
  public void buildInstanceMethodCallWithArgs() {
    int instanceId = 942389;
    List<Argument> args =
        Arrays.asList(new Argument("Hello, World!", "String"), new Argument(23823));
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceMethodCall("SomeClass", "someMethod", instanceId, args);
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("call"));
    assertThat(request.getParams().getMethod(), is("someMethod"));
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertNull(request.getParams().getField());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getArgs(), is(args));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            createdId, "SomeClass", "someMethod", instanceId, args);
    assertThat(request2, is(request));

    // create another request with instance as ObjectRef, and test equality
    JsonRpcRequest request3 =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            createdId, "SomeClass", "someMethod", ObjectRef.from(instanceId), args);
    assertThat(request3, is(request));
  }

  @Test
  public void buildStaticFieldGet() {
    JsonRpcRequest request = JsonRpcMessageFactory.buildStaticFieldGet("SomeClass", "someField");
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("get"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getValue());
    assertNull(request.getParams().getInstance());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildStaticFieldGet(createdId, "SomeClass", "someField");
    assertThat(request2, is(request));
  }

  @Test
  public void buildInstanceFieldGet() {
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceFieldGet("SomeClass", instanceId, "someField");
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("get"));
    assertNull(request.getParams().getMethod());
    assertNull(request.getParams().getValue());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildInstanceFieldGet(
            createdId, "SomeClass", instanceId, "someField");
    assertThat(request2, is(request));

    // create another request with instance as ObjectRef, and test equality
    JsonRpcRequest request3 =
        JsonRpcMessageFactory.buildInstanceFieldGet(
            createdId, "SomeClass", ObjectRef.from(instanceId), "someField");
    assertThat(request3, is(request));
  }

  @Test
  public void buildStaticFieldPut() {
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildStaticFieldPut(
            "SomeClass", "someField", new Argument("Hello, World!", "String"));
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("put"));
    assertNull(request.getParams().getMethod());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertNull(request.getParams().getInstance());
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getValue(), is(new Argument("Hello, World!", "String")));
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildStaticFieldPut(
            createdId, "SomeClass", "someField", new Argument("Hello, World!", "String"));
    assertThat(request2, is(request));
  }

  @Test
  public void buildInstanceFieldPut() {
    int instanceId = 942389;
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            "SomeClass", instanceId, "someField", new Argument("Hello, World!", "String"));
    assertNotNull(request);
    assertNotNull(request.getId());
    assertThat(request.getMethod(), is("put"));
    assertNull(request.getParams().getMethod());
    assertThat(request.getParams().getType(), is("SomeClass"));
    assertThat(request.getParams().getInstance(), is(instanceId));
    assertThat(request.getParams().getField(), is("someField"));
    assertThat(request.getParams().getValue(), is(new Argument("Hello, World!", "String")));
    assertThat(request.getParams().getArgs(), is(empty()));

    // create another request with same parameters but supplying the Id, and test equality
    String createdId = request.getId();
    JsonRpcRequest request2 =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            createdId,
            "SomeClass",
            instanceId,
            "someField",
            new Argument("Hello, World!", "String"));
    assertThat(request2, is(request));

    // create another request with instance as ObjectRef, and test equality
    JsonRpcRequest request3 =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            createdId,
            "SomeClass",
            ObjectRef.from(instanceId),
            "someField",
            new Argument("Hello, World!", "String"));
    assertThat(request3, is(request));
  }
}
