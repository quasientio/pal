/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.types.MessageType;
import org.junit.Test;

public class ControlAndMetaMessageUtilsTest {

  @Test
  public void controlMessageType_requestVsResponse() {
    ControlMessage req = new ControlMessage();
    req.setStatus((byte) 0);
    ControlMessage resp = new ControlMessage();
    resp.setStatus((byte) 1);

    assertThat(ControlMessageUtils.getMessageTypeOf(req), is(MessageType.CONTROL_MESSAGE_REQUEST));
    assertThat(
        ControlMessageUtils.getMessageTypeOf(resp), is(MessageType.CONTROL_MESSAGE_RESPONSE));
  }

  @Test
  public void metaMessageType_requestVsResponse() {
    MetaMessage req = new MetaMessage();
    req.setStatus((byte) 0);
    MetaMessage resp = new MetaMessage();
    resp.setStatus((byte) 1);

    assertThat(MetaMessageUtils.getMessageTypeOf(req), is(MessageType.META_MESSAGE_REQUEST));
    assertThat(MetaMessageUtils.getMessageTypeOf(resp), is(MessageType.META_MESSAGE_RESPONSE));
  }
}
