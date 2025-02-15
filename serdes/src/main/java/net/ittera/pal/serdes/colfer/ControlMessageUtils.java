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

import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.types.MessageType;

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
