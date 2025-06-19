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

package com.quasient.pal.core.rpc.exec.java;

/**
 * Singleton utility class representing an absence of value.
 *
 * <p>This class provides a single, shared instance used in contexts where a "void" equivalent is
 * required, such as in generic RPC operations where a specific type must be returned instead of
 * Java's primitive void.
 */
@SuppressWarnings({"UtilityClass", "JavaLangClash"})
public class Void {

  /**
   * The unique singleton instance of this class.
   *
   * <p>This constant is initialized once and reused across the system to represent an empty or void
   * value in operations where a concrete instance is required.
   */
  private static final Void instance = new Void();

  /**
   * Private constructor to enforce the singleton pattern.
   *
   * <p>Instantiating this class externally is prevented to ensure that only one instance exists for
   * representing the absence of a meaningful value.
   */
  private Void() {}

  /**
   * Returns the singleton instance of {@code Void}.
   *
   * <p>This method provides access to the unique instance of this class, ensuring that the same
   * "void" value is used consistently across the system.
   *
   * @return the unique singleton instance of {@code Void}
   */
  static Void getInstance() {
    return instance;
  }
}
