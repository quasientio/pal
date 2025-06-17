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

package com.quasient.pal.core;

/**
 * Exception indicating that an operation referenced a session which does not exist.
 *
 * <p>This exception is intended for use in contexts where session management is performed,
 * signaling that a requested session (identified by an ID or other reference) could not be found.
 */
public class NoSuchSessionException extends Exception {

  /**
   * Constructs a new NoSuchSessionException without a detail message.
   *
   * <p>Use this constructor when no additional context is required regarding the missing session.
   */
  public NoSuchSessionException() {}

  /**
   * Constructs a new NoSuchSessionException with the specified detail message.
   *
   * @param message a descriptive message providing context for the exception
   */
  public NoSuchSessionException(String message) {
    super(message);
  }
}
