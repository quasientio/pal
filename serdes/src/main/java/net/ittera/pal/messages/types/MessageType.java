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

package net.ittera.pal.messages.types;

public enum MessageType {
  // EXEC (0 - 30)
  EXEC_CONSTRUCTOR(MessageFamily.EXEC, (byte) 1),
  EXEC_INSTANCE_METHOD(MessageFamily.EXEC, (byte) 2),
  EXEC_CLASS_METHOD(MessageFamily.EXEC, (byte) 3),
  EXEC_GET_STATIC(MessageFamily.EXEC, (byte) 4),
  EXEC_GET_FIELD(MessageFamily.EXEC, (byte) 5),
  EXEC_PUT_STATIC(MessageFamily.EXEC, (byte) 6),
  EXEC_PUT_FIELD(MessageFamily.EXEC, (byte) 7),
  EXEC_PUT_STATIC_DONE(MessageFamily.EXEC, (byte) 8),
  EXEC_PUT_FIELD_DONE(MessageFamily.EXEC, (byte) 9),
  EXEC_THROWABLE(MessageFamily.EXEC, (byte) 10),
  EXEC_RETURN_VALUE(MessageFamily.EXEC, (byte) 11),

  // CONTROL (31 - 50)
  // command messages
  CONTROL_MESSAGE_REQUEST(MessageFamily.CONTROL, (byte) 31),
  CONTROL_MESSAGE_RESPONSE(MessageFamily.CONTROL, (byte) 32),

  // status (i.e. reply) messages
  //  CONTROL_OK(MessageFamily.CONTROL, (byte) 41),
  //  CONTROL_ERROR(MessageFamily.CONTROL, (byte) 42),
  //  (the following maybe not needed, we could use a generic control_error message with a code and
  // message)
  //  CONTROL_UNAUTHORIZED(MessageFamily.CONTROL, (byte) 43),
  //  CONTROL_UNSUPPORTED_COMMAND(MessageFamily.CONTROL, (byte) 44),
  //  CONTROL_NO_SUCH_SESSION(MessageFamily.CONTROL, (byte) 45),
  //  CONTROL_NO_SUCH_OBJECT(MessageFamily.CONTROL, (byte) 46);

  // INTERCEPT (51 - 60)
  INTERCEPT_MESSAGE(MessageFamily.INTERCEPT, (byte) 51),
  INTERCEPT_KEY(MessageFamily.INTERCEPT, (byte) 52),
  INTERCEPT_REPLY(MessageFamily.INTERCEPT, (byte) 53),

  // META (61 - 80)
  META_MESSAGE_REQUEST(MessageFamily.META, (byte) 60),
  META_MESSAGE_REPLY(MessageFamily.META, (byte) 61);

  private final MessageFamily family;
  private final byte id;

  MessageType(MessageFamily family, byte id) {
    this.family = family;
    this.id = id;
  }

  public MessageFamily getFamily() {
    return family;
  }

  public byte getId() {
    return id;
  }

  private static final MessageType[] LOOKUP = new MessageType[256];

  static {
    for (MessageType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  public static MessageType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }
}
