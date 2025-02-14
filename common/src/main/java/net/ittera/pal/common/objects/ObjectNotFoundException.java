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

package net.ittera.pal.common.objects;

/** Thrown to indicate that a specific object was not found. */
public class ObjectNotFoundException extends Exception {

  /**
   * Constructs a new {@code ObjectNotFoundException} with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public ObjectNotFoundException(String message) {
    super(message);
  }
}
