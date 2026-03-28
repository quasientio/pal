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
package io.quasient.pal.messages.types;

import java.util.Locale;

/**
 * Enumerates the different categories of messages used within the system. Each {@code
 * MessageFamily} constant defines a specific family of messages for processing and handling various
 * operations.
 */
public enum MessageFamily {

  /** Represents control-related messages used to issue runtime operations. */
  CONTROL,

  /** Represents execution messages, such as constructor/method calls and field operations. */
  EXEC,

  /** Represents messages that intercept exec messages. */
  INTERCEPT,

  /** Represents metadata messages. */
  META;

  /**
   * Retrieves the JSON-compatible name of this message family.
   *
   * @return the lowercase string representation of the message family name.
   */
  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
