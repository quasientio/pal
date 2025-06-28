/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.rpc.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.types.JsonRpcErrorCode;
import com.quasient.pal.serdes.Unwrapper;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface JsonRpcMessageAssertions {

  Logger logger = LoggerFactory.getLogger("tests");

  default void assertErrorResponse(
      Integer requestId,
      JsonRpcResponse response,
      JsonRpcErrorCode expectedErrorCode,
      String expectedThrowableType,
      String expectedMessageContains) {
    assertErrorResponse(requestId, response, expectedErrorCode, expectedThrowableType);

    // Check that the message contains the expected message
    assertThat(response.getError(), is(not(nullValue())));
    assertThat(response.getError().getData(), is(not(nullValue())));
    assertThat(response.getError().getData().getMessage(), containsString(expectedMessageContains));
  }

  default void assertErrorResponse(
      Integer requestId,
      JsonRpcResponse response,
      JsonRpcErrorCode expectedErrorCode,
      @Nullable String expectedThrowableType) {
    assertThat(response.getResult(), is(nullValue()));
    assertThat(response.getError(), is(not(nullValue())));
    assertThat(response.getError().getCode(), is(expectedErrorCode.getCode()));
    assertThat(response.getError().getMessage(), is(expectedErrorCode.getMessage()));
    assertThat(response.getError().getData(), is(not(nullValue())));
    if (expectedThrowableType != null) {
      assertThat(response.getError().getData().getThrowableType(), is(expectedThrowableType));
    }
    assertThat(response.getError().getData().getRequestId(), is(String.valueOf(requestId)));
  }

  default void assertPutResultIsVoid(JsonRpcResponse putResponse) {
    assertNotNull(putResponse.getResult());
    assertTrue(putResponse.getResult().getIsVoid());
    assertThat(putResponse.getResult().getValue(), is(nullValue()));
    assertThat(putResponse.getError(), is(nullValue()));
  }

  default <T> void assertResultEqualsTypeAndValue(JsonRpcResponse response, Class<T> type, T value)
      throws ClassNotFoundException {
    assertThat(response.getResult(), is(not(nullValue())));
    assertThat(response.getResult().getIsVoid(), is(false));
    assertThat(response.getResult().getValue(), is(not(nullValue())));
    Object resultValue = Unwrapper.unwrapObject(response.getResult().getValue());
    if (value == null) {
      assertNull(resultValue);
    } else {
      assertTrue(type.isInstance(resultValue));
      assertThat(resultValue, is(value));
    }
  }

  // <editor-fold desc="Array assertions">
  default Object assertValueIsArrayOfType(JsonRpcResponse response, String typeName)
      throws ClassNotFoundException {
    assertNotNull(response.getResult());
    assertThat(response.getResult().getIsVoid(), is(false));
    assertThat(response.getResult().getValue(), is(not(nullValue())));
    assertThat(response.getResult().getValue().getType(), is(typeName));
    Object resultValue = Unwrapper.unwrapObject(response.getResult().getValue());
    assertThat(resultValue, is(not(nullValue())));
    assertThat(resultValue.getClass().getName(), is(typeName));
    return resultValue;
  }

  default void assertValueIsNullArrayOfType(JsonRpcResponse response, String type)
      throws ClassNotFoundException {
    assertThat(response.getResult(), is(not(nullValue())));
    assertThat(response.getResult().getIsVoid(), is(false));
    assertThat(response.getResult().getValue(), is(not(nullValue())));
    assertThat(response.getResult().getValue().getType(), is(type));
    Object resultValue = Unwrapper.unwrapObject(response.getResult().getValue());
    assertThat(resultValue, is(nullValue()));
  }
  // </editor-fold>
}
