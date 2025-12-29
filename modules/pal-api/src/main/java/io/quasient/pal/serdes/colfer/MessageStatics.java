/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Immutable metadata for a message-related method or field.
 *
 * <p>Holds a reference to the declaring message class, its member name, modifiers, and (for
 * methods) parameter information. Instances are thread-safe and read-only.
 */
final class MessageStatics {

  /** The declaring message class. */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  final io.quasient.pal.messages.colfer.Class clazz;

  /** The method or field name. */
  final String name;

  /** Modifiers as defined in {@link java.lang.reflect.Modifier}. */
  final int modifiers;

  /** Parameter names, or {@code null} if omitted. */
  @SuppressWarnings("UnusedVariable")
  @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "Kept for data completeness")
  final String[] paramNames;

  /** Fully qualified parameter type names, in declaration order. */
  final String[] paramTypeNames;

  /**
   * Creates a new {@code MessageStatics} instance.
   *
   * @param clazz the declaring message class; not {@code null}
   * @param name the method or field name; not {@code null}
   * @param modifiers Java language modifiers bitmask
   * @param paramNames parameter names, or {@code null} if omitted
   * @param paramTypeNames fully qualified parameter type names; not {@code null}
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  MessageStatics(
      io.quasient.pal.messages.colfer.Class clazz,
      String name,
      int modifiers,
      String[] paramNames,
      String[] paramTypeNames) {
    this.clazz = clazz;
    this.name = name;
    this.modifiers = modifiers;
    this.paramNames = paramNames;
    this.paramTypeNames = paramTypeNames;
  }
}
