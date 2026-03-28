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
