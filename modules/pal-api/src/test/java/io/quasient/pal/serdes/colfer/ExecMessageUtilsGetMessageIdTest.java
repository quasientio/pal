/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MessageType;
import java.util.UUID;
import org.junit.Test;

public class ExecMessageUtilsGetMessageIdTest {

  @Test
  public void getMessageId_exec_family() {
    ExecMessage e = new ExecMessage().withMessageId("e1");
    Message m = new Message();
    m.setMessageType(MessageType.EXEC_CLASS_METHOD.getId());
    m.setExecMessage(e);
    assertThat(ExecMessageUtils.getMessageId(m), is("e1"));
  }

  @Test
  public void getMessageId_control_family() {
    ControlMessage c = new ControlMessage().withMessageId("c1");
    Message m = new Message();
    m.setMessageType(MessageType.CONTROL_MESSAGE_REQUEST.getId());
    m.setControlMessage(c);
    assertThat(ExecMessageUtils.getMessageId(m), is("c1"));
  }

  @Test
  public void getMessageId_meta_family() {
    MetaMessage mm = new MetaMessage().withMessageId("m1");
    Message m = new Message();
    m.setMessageType(MessageType.META_MESSAGE_RESPONSE.getId());
    m.setMetaMessage(mm);
    assertThat(ExecMessageUtils.getMessageId(m), is("m1"));
  }

  @Test
  public void getMessageId_intercept_family() {
    InterceptMessage im = new InterceptMessage().withMessageId(UUID.randomUUID().toString());
    Message m = new Message();
    m.setMessageType(MessageType.INTERCEPT_MESSAGE.getId());
    m.setInterceptMessage(im);
    assertThat(ExecMessageUtils.getMessageId(m), is(im.getMessageId()));
  }
}
