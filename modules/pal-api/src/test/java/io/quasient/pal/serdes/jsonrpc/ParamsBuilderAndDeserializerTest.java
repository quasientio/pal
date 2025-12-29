/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.Params;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

public class ParamsBuilderAndDeserializerTest {

  @Test
  public void params_withArrayAndMapValues_roundTrip() throws Exception {
    // Build Params with arguments including array and primitive types
    Argument arrArg =
        Argument.builder().withName("array").withValue(new Integer[] {1, 2, 3}).build();
    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
    m.put("k1", 1);
    m.put("k2", "v2");
    Argument mapArg = Argument.builder().withName("map").withValue(m).build();
    Argument boolArg = Argument.builder().withName("flag").withValue(true).build();

    Params p =
        Params.builder()
            .withType("java.util.Arrays")
            .withMethod("asList")
            .withArgs(List.of(arrArg, mapArg, boolArg))
            .build();

    JsonRpcRequest req =
        JsonRpcRequest.builder().withId("1").withMethod("call").withParams(p).build();
    String json = JsonRpcSerializer.toJson(req);
    JsonRpcRequest parsed = JsonRpcSerializer.fromJson(json, JsonRpcRequest.class);

    // basic checks that the adapter preserved essential fields and args count
    assertThat(parsed.getParams().getType(), is("java.util.Arrays"));
    assertThat(parsed.getParams().getMethod(), is("asList"));
    assertThat(parsed.getParams().getArgs().size(), is(3));

    // Validate the array value was preserved (as List or as array depending on type hints)
    Object arrayParsed = parsed.getParams().getArgs().get(0).getValue();
    if (arrayParsed instanceof List) {
      assertThat(((List<?>) arrayParsed).size(), is(3));
    } else if (arrayParsed != null && arrayParsed.getClass().isArray()) {
      assertThat(Array.getLength(arrayParsed), is(3));
    } else {
      fail("Expected list or array for first argument value");
    }
  }
}
