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
import static org.hamcrest.Matchers.*;

import org.junit.Test;

public class LogMessageHeaderTest {

  @Test
  public void equalsHashCodeAndToString() {
    LogMessageHeader h1 = new LogMessageHeader("k", new byte[] {1, 2});
    LogMessageHeader h2 = new LogMessageHeader("k", new byte[] {1, 2});
    LogMessageHeader h3 = new LogMessageHeader("k", new byte[] {2, 1});

    assertThat(h1, is(h2));
    assertThat(h1.hashCode(), is(h2.hashCode()));
    assertThat(h1.equals(h3), is(false));
    assertThat(h1.toString(), containsString("key='k'"));
    assertThat(h1.key(), is("k"));
    assertThat(h1.value(), is(new byte[] {1, 2}));
  }
}
