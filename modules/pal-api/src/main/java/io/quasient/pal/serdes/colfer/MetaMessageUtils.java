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
