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

package net.ittera.pal.tools;

import net.ittera.pal.messages.protobuf.Wrappers;

public class AbstractTool {
  protected static String getPeerUuid(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getPeerUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getPeerUuid();
    }
    return null;
  }

  protected static String getMessageUuid(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMessageUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getMessageUuid();
      //    } else if (msg.hasInterceptReply()) {
      //      return msg.getInterceptReply().getMessageUuid();
    }
    return null;
  }

  protected static String getMessageType(Wrappers.Message msg) {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMsgType().name();
    } else if (msg.hasInterceptMessage()) {
      return "InterceptMessage";
    } else if (msg.hasInterceptReply()) {
      return "InterceptReply";
    }
    return null;
  }
}
