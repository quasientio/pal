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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class MessageContextTest {

  @Test
  public void fieldsAndToString() {
    MessageContext mc = new MessageContext(42L, 1, 1000L, "topic-x", "log-1");
    assertThat(mc.offset(), is(42L));
    assertThat(mc.partition(), is(1));
    assertThat(mc.timestamp(), is(1000L));
    assertThat(mc.topic(), is("topic-x"));
    assertThat(mc.logId(), is("log-1"));
    String s = mc.toString();
    assertThat(s, containsString("offset=42"));
    assertThat(s, containsString("topic='topic-x'"));
  }
}
