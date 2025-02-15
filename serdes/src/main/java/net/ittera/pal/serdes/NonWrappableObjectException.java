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

package net.ittera.pal.serdes;

/**
 * This exception is thrown when an attempt is made to wrap an object that is not supported by the
 * serialization process.
 */
public class NonWrappableObjectException extends IllegalArgumentException {

  /** The object that could not be wrapped. */
  private final transient Object nonWrappableObject;

  /**
   * Constructs a new NonWrappableObjectException with the specified object.
   *
   * @param nonWrappableObject the object that cannot be wrapped
   */
  public NonWrappableObjectException(Object nonWrappableObject) {
    this.nonWrappableObject = nonWrappableObject;
  }

  /**
   * Constructs a new NonWrappableObjectException with the specified detail message and object.
   *
   * @param message the detail message explaining the reason for the exception
   * @param nonWrappableObject the object that cannot be wrapped
   */
  public NonWrappableObjectException(String message, Object nonWrappableObject) {
    super(message);
    this.nonWrappableObject = nonWrappableObject;
  }

  /**
   * Returns the object that could not be wrapped.
   *
   * @return the non-wrappable object
   */
  public Object getNonWrappableObject() {
    return nonWrappableObject;
  }
}
