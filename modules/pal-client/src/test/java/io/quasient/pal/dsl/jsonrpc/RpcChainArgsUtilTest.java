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
package io.quasient.pal.dsl.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class RpcChainArgsUtilTest {

  @Test
  public void args_returnsSameElements_inOrder() {
    Object a = "a";
    Object b = 1;
    Object c = new Object();
    Object[] arr = RpcChain.args(a, b, c);
    assertThat(arr.length, is(3));
    assertThat(arr[0], is(a));
    assertThat(arr[1], is(b));
    assertThat(arr[2], is(c));
  }
}
