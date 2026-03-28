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

import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import io.quasient.pal.serdes.jsonrpc.JsonSerializationException;
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
