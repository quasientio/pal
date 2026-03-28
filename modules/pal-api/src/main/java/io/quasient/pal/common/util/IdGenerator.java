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
package io.quasient.pal.common.util;

/**
 * Provides a mechanism for generating unique identifiers.
 *
 * <p>Implementations of this interface should ensure that each ID generated is unique across the
 * system. This is useful for identifying entities, messages, or other components that require
 * distinct identification.
 */
public interface IdGenerator {
  /**
   * Generates the next unique identifier.
   *
   * @return a unique identifier as a {@code String}.
   */
  String nextId();
}
