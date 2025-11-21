/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static com.quasient.pal.messages.types.JsonRpcErrorCode.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.quasient.pal.messages.jsonrpc.JsonRpcError;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.serdes.jsonrpc.InvalidJsonRpcParamsException;
import com.quasient.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import com.quasient.pal.serdes.jsonrpc.JsonRpcParseException;
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
