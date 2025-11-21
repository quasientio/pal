/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import org.junit.Test;

public class JsonRpcResponseTest {

  @Test
  public void testSuccessResponseUsingBuilder() throws JsonSerializationException {
    // Create a successful response
    JsonRpcResponseReturnValue jsonRpcResponseReturnValue =
        new JsonRpcResponseReturnValue.Builder().withIsVoid(true).build();

    JsonRpcResponse response =
        new JsonRpcResponse.Builder().withId(1).withResult(jsonRpcResponseReturnValue).build();

    // Serialize the response
    String jsonString = JsonRpcSerializer.toJson(response);

    // Deserialize the response back
    JsonRpcResponse deserializedResponse =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcResponse.class);

    // Verify the fields
    assertNotNull(deserializedResponse);
    assertEquals("2.0", deserializedResponse.getJsonrpc());
    assertThat(deserializedResponse.getId(), is("1"));
    assertNull(deserializedResponse.getError());
    assertNotNull(deserializedResponse.getResult());
    assertThat(deserializedResponse.getResult().getIsVoid(), is(true));
  }

  @Test
  public void testSuccessResponseUsingSetters() throws JsonSerializationException {
    // Create a successful response
    JsonRpcResponseReturnValue jsonRpcResponseReturnValue = new JsonRpcResponseReturnValue();
    jsonRpcResponseReturnValue.setIsVoid(true);

    JsonRpcResponse response = new JsonRpcResponse();
    response.setId("1");
    response.setResult(jsonRpcResponseReturnValue);

    // Serialize the response
    String jsonString = JsonRpcSerializer.toJson(response);

    // Deserialize the response back
    JsonRpcResponse deserializedResponse =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcResponse.class);

    // Verify the fields
    assertNotNull(deserializedResponse);
    assertEquals("2.0", deserializedResponse.getJsonrpc());
    assertThat(deserializedResponse.getId(), is("1"));
    assertNull(deserializedResponse.getError());
    assertNotNull(deserializedResponse.getResult());
    assertThat(deserializedResponse.getResult().getIsVoid(), is(true));
  }

  @Test
  public void testErrorResponseUsingBuilder() throws JsonSerializationException {
    // Create an error response
    JsonRpcError error = new JsonRpcError(-32601, "Method not found");
    JsonRpcResponse response = new JsonRpcResponse.Builder().withError(error).withId(1).build();

    // Serialize the response
    String jsonString = JsonRpcSerializer.toJson(response);

    // Deserialize the response back
    JsonRpcResponse deserializedResponse =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcResponse.class);

    // Verify the fields
    assertNotNull(deserializedResponse);
    assertEquals("2.0", deserializedResponse.getJsonrpc());
    assertThat(deserializedResponse.getId(), is("1"));
    assertNull(deserializedResponse.getResult());
    assertNotNull(deserializedResponse.getError());
    assertThat(deserializedResponse.getError().getCode(), is(-32601));
    assertEquals("Method not found", deserializedResponse.getError().getMessage());
  }

  @Test
  public void testErrorResponseUsingSetters() throws JsonSerializationException {
    // Create an error response
    JsonRpcError error = new JsonRpcError();
    error.setCode(-32601);
    error.setMessage("Method not found");

    JsonRpcResponse response = new JsonRpcResponse();
    response.setId("1");
    response.setError(error);

    // Serialize the response
    String jsonString = JsonRpcSerializer.toJson(response);

    // Deserialize the response back
    JsonRpcResponse deserializedResponse =
        JsonRpcSerializer.fromJson(jsonString, JsonRpcResponse.class);

    // Verify the fields
    assertNotNull(deserializedResponse);
    assertEquals("2.0", deserializedResponse.getJsonrpc());
    assertThat(deserializedResponse.getId(), is("1"));
    assertNull(deserializedResponse.getResult());
    assertNotNull(deserializedResponse.getError());
    assertThat(deserializedResponse.getError().getCode(), is(-32601));
    assertEquals("Method not found", deserializedResponse.getError().getMessage());
  }
}
