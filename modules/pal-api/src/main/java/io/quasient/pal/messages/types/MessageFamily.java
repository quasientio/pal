/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
