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

package net.ittera.pal.messages;

import java.util.Map;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;

public record LogMessage<T>(long offset, Map<String, String> headers, T content) {
  public LogMessage {
    if (!(content instanceof Message || content instanceof JsonRpcMessage)) {
      throw new IllegalArgumentException("content must be a Message or JsonRpcMessage");
    }
  }

  @Override
  public String toString() {
    return "LogMessage{"
        + "offset="
        + offset
        + ", headers="
        + headers
        + ", content="
        + content
        + '}';
  }
}
