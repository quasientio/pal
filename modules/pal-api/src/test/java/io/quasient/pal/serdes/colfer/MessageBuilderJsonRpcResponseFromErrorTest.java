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
package io.quasient.pal.serdes.colfer;

import static io.quasient.pal.messages.types.JsonRpcErrorCode.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcParamsException;
import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import io.quasient.pal.serdes.jsonrpc.JsonRpcParseException;
import java.util.UUID;
import org.junit.Test;

public class MessageBuilderJsonRpcResponseFromErrorTest {

  @Test
  public void from_parse_exception_mapsTo_PARSE_ERROR() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = "10";
    Exception parsingCause = new IllegalArgumentException("bad json");
    JsonRpcParseException ex = new JsonRpcParseException(parsingCause, rid);

    JsonRpcResponse r = b.jsonRpcResponseFromError(ex, rid);
    assertEquals(rid, r.getId());
    JsonRpcError e = r.getError();
    assertNotNull(e);
    assertEquals(PARSE_ERROR.getCode(), e.getCode());
    assertEquals(PARSE_ERROR.getMessage(), e.getMessage());
    assertThat(e.getData().getRequestId(), is(rid));
    assertThat(e.getData().getThrowableType(), is(parsingCause.getClass().getName()));
    assertThat(e.getData().getMessage(), is(parsingCause.getMessage()));
  }

  @Test
  public void from_invalid_params_mapsTo_INVALID_PARAMS() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = "11";
    InvalidJsonRpcParamsException ex = new InvalidJsonRpcParamsException("bad", rid);

    JsonRpcError e = b.jsonRpcResponseFromError(ex, rid).getError();
    assertEquals(INVALID_PARAMS.getCode(), e.getCode());
    assertEquals(INVALID_PARAMS.getMessage(), e.getMessage());
    assertThat(e.getData().getRequestId(), is(rid));
    assertThat(e.getData().getMessage(), is("bad"));
  }

  @Test
  public void from_invalid_request_mapsTo_INVALID_REQUEST() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = "12";
    InvalidJsonRpcRequestException ex = new InvalidJsonRpcRequestException("nope", rid);

    JsonRpcError e = b.jsonRpcResponseFromError(ex, rid).getError();
    assertEquals(INVALID_REQUEST.getCode(), e.getCode());
    assertEquals(INVALID_REQUEST.getMessage(), e.getMessage());
    assertThat(e.getData().getRequestId(), is(rid));
    assertThat(e.getData().getMessage(), is("nope"));
  }

  @Test
  public void from_generic_exception_mapsTo_SERVER_ERROR() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = "13";
    RuntimeException ex = new RuntimeException("boom");

    JsonRpcError e = b.jsonRpcResponseFromError(ex, rid).getError();
    assertEquals(SERVER_ERROR.getCode(), e.getCode());
    assertEquals(SERVER_ERROR.getMessage(), e.getMessage());
    assertThat(e.getData().getRequestId(), is(rid));
    assertThat(e.getData().getThrowableType(), is(RuntimeException.class.getName()));
    assertThat(e.getData().getMessage(), is("boom"));
  }
}
