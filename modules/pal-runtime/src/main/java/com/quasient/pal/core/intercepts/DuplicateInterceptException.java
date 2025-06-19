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

package com.quasient.pal.core.intercepts;

/** Exception thrown when attempting to register an intercept that has already been established. */
public class DuplicateInterceptException extends Exception {

  /**
   * Constructs a new DuplicateInterceptException with the specified detail message.
   *
   * @param message the detail message describing the duplicate intercept scenario
   */
  public DuplicateInterceptException(String message) {
    super(message);
  }
}
