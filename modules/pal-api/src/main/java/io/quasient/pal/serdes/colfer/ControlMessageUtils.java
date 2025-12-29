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

import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.types.MessageType;

/**
 * Utility class for operations related to {@link ControlMessage}. Provides methods to determine
 * message types based on control message status.
 */
public class ControlMessageUtils {

  /**
   * Determines the {@link MessageType} of a given {@link ControlMessage}.
   *
   * @param controlMessage the control message whose type is to be determined
   * @return {@link MessageType#CONTROL_MESSAGE_REQUEST} if the status is 0, {@link
   *     MessageType#CONTROL_MESSAGE_RESPONSE} otherwise
   */
  public static MessageType getMessageTypeOf(ControlMessage controlMessage) {
    if (controlMessage.getStatus() == 0) {
      return MessageType.CONTROL_MESSAGE_REQUEST;
    } else {
      return MessageType.CONTROL_MESSAGE_RESPONSE;
    }
  }
}
