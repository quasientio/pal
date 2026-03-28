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
