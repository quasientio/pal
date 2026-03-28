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
package io.quasient.pal.messages.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcParamsException;
import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import io.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class JsonRpcRequestTest {

  @Test
  public void testNewRequest() throws JsonSerializationException {
    // Create a 'new' request (constructor call)
    Params newParams =
        new Params.Builder()
            .withType("java.lang.String")
            .addArg(new Argument.Builder().withValue("Hello, World!").build())
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(1).withMethod("new").withParams(newParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("1"));
    assertEquals("new", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("java.lang.String", deserializedParams.getType());
    assertNotNull(deserializedParams.getArgs());
    assertThat(deserializedParams.getArgs().size(), is(1));
    assertEquals("Hello, World!", deserializedParams.getArgs().get(0).getValue());
  }

  @Test
  public void testCallInstanceMethodRequest() throws JsonSerializationException {
    // Create a 'call' instance method request
    Params callParams =
        new Params.Builder()
            .withType("io.quasient.pal.examples.MyBookReader")
            .withMethod("readPage")
            .withInstance(1234)
            .addArg(new Argument.Builder().withValue(4).build())
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(2).withMethod("call").withParams(callParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("2"));
    assertEquals("call", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("io.quasient.pal.examples.MyBookReader", deserializedParams.getType());
    assertEquals("readPage", deserializedParams.getMethod());
    assertThat(deserializedParams.getInstance(), is(1234));
    assertNotNull(deserializedParams.getArgs());
    assertThat(deserializedParams.getArgs().size(), is(1));
    assertThat(deserializedParams.getArgs().get(0).getValue(), is(4));
  }

  @Test
  public void testCallStaticMethodRequest() throws JsonSerializationException {
    // Create a 'call' static method request
    Params callParams =
        new Params.Builder()
            .withType("java.lang.Math")
            .withMethod("abs")
            .addArg(new Argument.Builder().withValue(-10).build())
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(3).withMethod("call").withParams(callParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("3"));
    assertEquals("call", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("java.lang.Math", deserializedParams.getType());
    assertEquals("abs", deserializedParams.getMethod());
    assertNull(deserializedParams.getInstance());
    assertNotNull(deserializedParams.getArgs());
    assertThat(deserializedParams.getArgs().size(), is(1));
    assertThat(deserializedParams.getArgs().get(0).getValue(), is(-10));
  }

  @Test
  public void testCallMethodWithValueAndReferenceArgs() throws JsonSerializationException {
    // Create arguments
    Argument valueArg = new Argument();
    valueArg.setValue(42);
    Argument refArg = new Argument();
    refArg.setRef(5678);

    Argument typedArg = new Argument();
    typedArg.setValue("Hello");
    typedArg.setType("java.lang.String");

    // Create Params
    Params callParams = new Params();
    callParams.setType("com.example.MyClass");
    callParams.setMethod("myMethod");
    callParams.setInstance(1234);
    callParams.setArgs(Arrays.asList(valueArg, refArg, typedArg));

    // Create JsonRpcRequest
    JsonRpcRequest request = new JsonRpcRequest();
    request.setId(1);
    request.setMethod("call");
    request.setParams(callParams);

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    Params deserializedParams = deserializedRequest.getParams();
    List<Argument> deserializedArgs = deserializedParams.getArgs();

    // First argument (value)
    assertNull(deserializedArgs.get(0).getRef());
    assertThat(deserializedArgs.get(0).getValue(), is(42));

    // Second argument (reference)
    assertNull(deserializedArgs.get(1).getValue());
    assertNotNull(deserializedArgs.get(1).getRef());
    assertEquals(5678, deserializedArgs.get(1).getRef().intValue());

    // Third argument (value with type)
    assertNull(deserializedArgs.get(2).getRef());
    assertEquals("Hello", deserializedArgs.get(2).getValue());
    assertEquals("java.lang.String", deserializedArgs.get(2).getType());
  }

  @Test
  public void testGetFieldRequest() throws JsonSerializationException {
    // Create a 'get' field request
    Params getParams =
        new Params.Builder()
            .withType("io.quasient.pal.examples.MyBookReader")
            .withField("currentPage")
            .withInstance(1234)
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(4).withMethod("get").withParams(getParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("4"));
    assertEquals("get", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("io.quasient.pal.examples.MyBookReader", deserializedParams.getType());
    assertEquals("currentPage", deserializedParams.getField());
    assertThat(deserializedParams.getInstance(), is(1234));
  }

  @Test
  public void testPutFieldRequest() throws JsonSerializationException {
    // Create a 'put' field request
    Params putParams =
        new Params.Builder()
            .withType("io.quasient.pal.examples.MyBookReader")
            .withField("currentPage")
            .withInstance(1234)
            .withValue(new Argument.Builder().withValue(10).build())
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(5).withMethod("put").withParams(putParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("5"));
    assertEquals("put", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("io.quasient.pal.examples.MyBookReader", deserializedParams.getType());
    assertEquals("currentPage", deserializedParams.getField());
    assertThat(deserializedParams.getInstance(), is(1234));
    assertNotNull(deserializedParams.getValue());
    assertThat(deserializedParams.getValue().getValue(), is(10));
  }

  @Test
  public void testPutFieldRequestWithNullValue() throws JsonSerializationException {
    // Create a 'put' field request with null value
    Params putParams =
        new Params.Builder()
            .withType("com.example.MyClass")
            .withField("myField")
            .withInstance(1234)
            .withValue(new Argument.Builder().withValue(null).build()) // Set value to null
            .build();

    JsonRpcRequest request =
        new JsonRpcRequest.Builder().withId(6).withMethod("put").withParams(putParams).build();

    // Serialize the request
    String jsonString = JsonRpcSerializer.toJson(request);

    // Deserialize the request back
    JsonRpcRequest deserializedRequest =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcRequest.class);

    // Verify the fields
    assertNotNull(deserializedRequest);
    assertEquals("2.0", deserializedRequest.getJsonrpc());
    assertThat(deserializedRequest.getId(), is("6"));
    assertEquals("put", deserializedRequest.getMethod());
    assertNotNull(deserializedRequest.getParams());

    Params deserializedParams = deserializedRequest.getParams();
    assertEquals("com.example.MyClass", deserializedParams.getType());
    assertEquals("myField", deserializedParams.getField());
    assertThat(deserializedParams.getInstance(), is(1234));
    Argument empty = new Argument();
    assertThat(
        deserializedParams.getValue(), is(empty)); // Value should be empty (effectively null)
  }

  /* Tests for illegal state when using build() */
  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildWithoutId() {
    new JsonRpcRequest.Builder().withMethod("new").withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildWithoutParams() {
    new JsonRpcRequest.Builder().withId(1).withMethod("new").build();
  }

  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildWithoutMethod() {
    new JsonRpcRequest.Builder().withId(1).withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildNewWithoutParamsType() {
    new JsonRpcRequest.Builder().withMethod("new").withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildCallWithoutParamsType() {
    new JsonRpcRequest.Builder().withMethod("call").withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildPutWithoutParamsType() {
    new JsonRpcRequest.Builder().withMethod("put").withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcRequestException.class)
  public void testBuildGetWithoutParamsType() {
    new JsonRpcRequest.Builder().withMethod("get").withParams(new Params()).build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildCallWithoutParamsMethod() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("call")
        .withParams(new Params.Builder().withType("java.lang.String").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildNewWithMethod() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("new")
        .withParams(
            new Params.Builder().withType("java.lang.String").withMethod("someMethod").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildPutWithMethod() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("put")
        .withParams(
            new Params.Builder().withType("java.lang.String").withMethod("someMethod").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildGetWithMethod() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("get")
        .withParams(
            new Params.Builder().withType("java.lang.String").withMethod("someMethod").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildPutWithoutField() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("put")
        .withParams(new Params.Builder().withType("java.lang.String").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildGetWithoutField() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("get")
        .withParams(new Params.Builder().withType("java.lang.String").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildNewWithField() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("new")
        .withParams(
            new Params.Builder().withType("java.lang.String").withField("someField").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildCallWithField() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("call")
        .withParams(
            new Params.Builder().withType("java.lang.String").withField("someField").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildPutWithoutValue() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("put")
        .withParams(
            new Params.Builder().withType("java.lang.String").withField("someField").build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildNewWithValue() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("new")
        .withParams(
            new Params.Builder()
                .withType("java.lang.String")
                .withValue(new Argument.Builder().withValue("Hello, World!").build())
                .build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildCallWithValue() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("call")
        .withParams(
            new Params.Builder()
                .withType("java.lang.String")
                .withValue(new Argument.Builder().withValue("Hello, World!").build())
                .build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildGetWithValue() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("get")
        .withParams(
            new Params.Builder()
                .withType("java.lang.String")
                .withValue(new Argument.Builder().withValue("Hello, World!").build())
                .build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildGetWithArgs() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("get")
        .withParams(
            new Params.Builder()
                .withType("java.lang.String")
                .addArg(new Argument.Builder().withValue("Hello, World!").build())
                .build())
        .build();
  }

  @Test(expected = InvalidJsonRpcParamsException.class)
  public void testBuildPutWithArgs() {
    new JsonRpcRequest.Builder()
        .withId(1)
        .withMethod("put")
        .withParams(
            new Params.Builder()
                .withType("java.lang.String")
                .withValue(new Argument.Builder().withValue("Hello, World!").build())
                .addArg(new Argument.Builder().withValue("Hello, World!").build())
                .build())
        .build();
  }
}
