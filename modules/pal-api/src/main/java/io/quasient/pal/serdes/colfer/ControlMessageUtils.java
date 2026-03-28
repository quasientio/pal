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
