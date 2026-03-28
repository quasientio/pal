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
package io.quasient.pal.core.rpc.policy;

import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Java member visibility levels for RPC policy evaluation.
 *
 * <p>Maps Java access modifiers (public, protected, package-private, private) to enum constants,
 * enabling RPC policy rules to gate access based on the visibility of the target member.
 *
 * <p>Use {@link #fromModifiers(int)} to derive visibility from {@link java.lang.reflect.Modifier}
 * bitmasks, and {@link #fromString(String)} to parse visibility from YAML configuration strings.
 */
public enum MemberVisibility {

  /** A public member ({@code public} keyword). */
  PUBLIC,

  /** A protected member ({@code protected} keyword). */
  PROTECTED,

  /** A package-private member (no explicit access modifier; also known as "default" visibility). */
  PACKAGE_PRIVATE,

  /** A private member ({@code private} keyword). */
  PRIVATE;

  /**
   * Derives the visibility level from Java reflection modifier bitmasks.
   *
   * <p>Uses {@link Modifier#isPublic(int)}, {@link Modifier#isProtected(int)}, and {@link
   * Modifier#isPrivate(int)} to determine the access level. When none of these match (including
   * when {@code modifiers} is {@code 0}), falls through to {@link #PACKAGE_PRIVATE}.
   *
   * @param modifiers the modifier bitmask from {@link java.lang.reflect.Member#getModifiers()}
   * @return the corresponding visibility level
   */
  public static MemberVisibility fromModifiers(int modifiers) {
    if (Modifier.isPublic(modifiers)) {
      return PUBLIC;
    }
    if (Modifier.isProtected(modifiers)) {
      return PROTECTED;
    }
    if (Modifier.isPrivate(modifiers)) {
      return PRIVATE;
    }
    return PACKAGE_PRIVATE;
  }

  /**
   * Parses a visibility level from a string representation.
   *
   * <p>Parsing is case-insensitive. The string {@code "DEFAULT"} is accepted as an alias for {@link
   * #PACKAGE_PRIVATE}, reflecting Java's convention of calling package-private visibility
   * "default." The string {@code "ALL"} returns {@code null}, serving as a sentinel value that
   * indicates all visibility levels should match.
   *
   * @param name the string to parse (case-insensitive)
   * @return the corresponding visibility level, or {@code null} if {@code name} is {@code "ALL"}
   * @throws IllegalArgumentException if the string does not match any known visibility level
   */
  public static MemberVisibility fromString(String name) {
    if ("DEFAULT".equalsIgnoreCase(name)) {
      return PACKAGE_PRIVATE;
    }
    if ("ALL".equalsIgnoreCase(name)) {
      return null;
    }
    return valueOf(name.toUpperCase(Locale.ROOT));
  }
}
