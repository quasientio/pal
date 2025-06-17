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

package com.quasient.pal.core.rpc;

/**
 * Signals that a provided message type is not supported in the current invocation context.
 *
 * <p>This exception extends {@link RuntimeException} and is thrown when an attempt is made to
 * process a message that is not recognized or supported by the Pal runtime.
 */
public class UnsupportedMessageException extends RuntimeException {

  /**
   * Constructs a new UnsupportedMessageException with the specified detail message.
   *
   * <p>The message parameter should provide details about the unsupported message type or cause.
   *
   * @param message the detail message identifying the unsupported message. May include context for
   *     debugging.
   */
  public UnsupportedMessageException(String message) {
    super(message);
  }
}
