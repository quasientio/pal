/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class MessageTypeTest {

  @Test
  public void testGetId() {
    MessageType controlMsg = MessageType.CONTROL_MESSAGE_REQUEST;
    MessageType interceptResponse = MessageType.INTERCEPT_RESPONSE;

    byte controlMsgAsByte = controlMsg.getId();
    byte interceptResponseMsgAsByte = interceptResponse.getId();

    assertThat(MessageType.fromId(controlMsgAsByte), is(MessageType.CONTROL_MESSAGE_REQUEST));
    assertThat(MessageType.fromId(interceptResponseMsgAsByte), is(MessageType.INTERCEPT_RESPONSE));
  }
}
