package com.quasient.pal.common.util;

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
