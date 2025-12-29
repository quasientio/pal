/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
