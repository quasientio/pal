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
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MessageType;
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
