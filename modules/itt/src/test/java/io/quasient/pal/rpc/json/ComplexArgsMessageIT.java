/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior. */
@RunWith(Parameterized.class)
public class ComplexArgsMessageIT extends AbstractJsonRpcMessageIT {

  private static final String CLASS_NAME = "io.quasient.pal.apps.quantized.rpc.Methods";

  public ComplexArgsMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  @Test
  public void callClassMethod_withArrayList_void() throws Exception {
    final String methodName = "nonVoidSumUpList";

    // 1. Create a new ArrayList instance with some integers
    Integer[] someIntegers = {39, 5, 58, 32, 70, 42};
    ArrayList<Integer> arrayList = new ArrayList<>();
    Collections.addAll(arrayList, someIntegers);

    // 2. Call the static method with the list as argument
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall(
            CLASS_NAME,
            methodName,
            List.of(
                Argument.builder().withValue(arrayList).withType("java.util.ArrayList").build()));

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is non void
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    Integer shouldReturn = Arrays.stream(someIntegers).mapToInt(Integer::intValue).sum();
    assertNotNull(responseMessage.getResult().getValue());
    Object rawObj = Unwrapper.unwrapObject(responseMessage.getResult().getValue());
    assertEquals(shouldReturn, rawObj);
  }

  @Test
  public void callClassMethod_withHashMap_void() throws Exception {
    final String methodName = "nonVoidSumUpMap";

    // 1. Create a new HashMap instance with some floats as values
    Float[] someFloats = {39f, 5.8f, -12f, 58.4f, 32f, 7f, 38338f, 42.98f};
    Map<String, Float> map = new HashMap<>();
    int idx = 0;
    for (float f : someFloats) {
      map.put(String.valueOf(f), someFloats[idx++]);
    }

    // 2. Call the static method with the list as argument
    JsonRpcRequest request =
        JsonRpcMessageFactory.buildClassMethodCall(
            CLASS_NAME,
            methodName,
            List.of(Argument.builder().withValue(map).withType("java.util.HashMap").build()));

    JsonRpcResponse responseMessage = sendAndReceive(request);

    // Assert that the result is not null
    assertNotNull(responseMessage.getResult());

    // Assert that returned value is non void
    assertThat(responseMessage.getResult().getIsVoid(), is(false));
    double shouldReturn = Arrays.stream(someFloats).mapToDouble(Float::floatValue).sum();
    Object rawObj = Unwrapper.unwrapObject(responseMessage.getResult().getValue());
    assertEquals(shouldReturn, (Float) rawObj, 0.001);
  }
}
