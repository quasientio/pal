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

public enum ExecMessageType {
  STATIC_CONSTRUCTOR,
  RETURN_CLASS,
  CONSTRUCTOR,
  INSTANCE_METHOD,
  CLASS_METHOD,
  GET_STATIC,
  GET_FIELD,
  PUT_STATIC,
  PUT_FIELD,
  PUT_STATIC_DONE,
  PUT_FIELD_DONE,
  THROWABLE,
  RETURN_VALUE,
}
