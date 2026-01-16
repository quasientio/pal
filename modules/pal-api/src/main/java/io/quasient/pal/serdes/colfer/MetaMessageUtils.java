/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MessageType;

/**
 * Provides utility methods for handling {@link MetaMessage} objects, such as determining the {@link
 * MessageType} of a given Meta message.
 */
public class MetaMessageUtils {

  /**
   * Determines the {@link MessageType} of the specified {@link MetaMessage} based on its status.
   *
   * @param metaMessage the {@link MetaMessage} instance whose message type is to be determined
   * @return {@link MessageType#META_MESSAGE_REQUEST} if the status of {@code metaMessage} is 0,
   *     {@link MessageType#META_MESSAGE_RESPONSE} otherwise
   */
  public static MessageType getMessageTypeOf(MetaMessage metaMessage) {
    if (metaMessage.getStatus() == 0) {
      return MessageType.META_MESSAGE_REQUEST;
    } else {
      return MessageType.META_MESSAGE_RESPONSE;
    }
  }
}
