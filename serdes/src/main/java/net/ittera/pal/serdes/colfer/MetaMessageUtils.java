/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import net.ittera.pal.messages.colfer.MetaMessage;
import net.ittera.pal.messages.types.MessageType;

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
