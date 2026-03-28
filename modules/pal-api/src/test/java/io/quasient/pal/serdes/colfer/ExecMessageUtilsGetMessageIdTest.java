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
