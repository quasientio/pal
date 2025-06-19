package com.quasient.pal.messages.types;

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
