/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.rpc.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
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

  private static final String CLASS_NAME = "com.quasient.pal.apps.rpc.Methods";

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
