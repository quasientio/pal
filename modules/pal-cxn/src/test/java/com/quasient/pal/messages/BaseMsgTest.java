/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;
import org.zeromq.ZMQ;

public class BaseMsgTest {

  private static class DummyMsg extends BaseMsg {
    @Override
    public boolean send(ZMQ.Socket socket) {
      return false;
    }
  }

  @Test
  public void sizeDefaultIsMinusOne() {
    DummyMsg m = new DummyMsg();
    assertThat(m.getSize(), is(-1));
  }
}
