/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.lang.reflect;

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
  public static Void getInstance() {
    return instance;
  }
}
