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
package io.quasient.pal.serdes.colfer;

/**
 * Specifies the policy for wrapping objects during serialization. Determines how object references
 * and values are managed to maintain referential integrity and performance.
 */
public enum WrapPolicy {

  /**
   * Prefers wrapping objects by reference to reduce redundancy and handle shared instances
   * efficiently. Suitable when maintaining object relationships and avoiding duplicate data is
   * essential.
   */
  PREFER_REFERENCE,

  /**
   * Forces wrapping objects by value, ensuring each object is serialized independently. Ideal when
   * object identity is not a concern and independent copies are required.
   */
  FORCE_BY_VALUE,

  /**
   * Automatically detects the optimal wrapping strategy based on the context and object structure.
   * Provides flexibility by selecting the most appropriate method at runtime.
   */
  DETECT
}
