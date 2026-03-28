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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Modifier;
import org.junit.Test;

/**
 * Unit tests for {@link MemberVisibility} enum.
 *
 * <p>Verifies that {@code MemberVisibility.fromModifiers()} correctly maps Java reflection modifier
 * bitmasks to visibility levels, that {@code MemberVisibility.fromString()} correctly parses string
 * representations including aliases, and that the enum contains exactly the expected set of values.
 */
public class MemberVisibilityTest {

  @Test
  public void shouldReturnPublicForPublicModifier() {
    // Given: java.lang.reflect.Modifier.PUBLIC bitmask
    int modifiers = Modifier.PUBLIC;

    // When: MemberVisibility.fromModifiers(modifiers) is called
    MemberVisibility result = MemberVisibility.fromModifiers(modifiers);

    // Then: Returns MemberVisibility.PUBLIC
    assertThat(result, is(MemberVisibility.PUBLIC));
  }

  @Test
  public void shouldReturnProtectedForProtectedModifier() {
    // Given: java.lang.reflect.Modifier.PROTECTED bitmask
    int modifiers = Modifier.PROTECTED;

    // When: MemberVisibility.fromModifiers(modifiers) is called
    MemberVisibility result = MemberVisibility.fromModifiers(modifiers);

    // Then: Returns MemberVisibility.PROTECTED
    assertThat(result, is(MemberVisibility.PROTECTED));
  }

  @Test
  public void shouldReturnPrivateForPrivateModifier() {
    // Given: java.lang.reflect.Modifier.PRIVATE bitmask
    int modifiers = Modifier.PRIVATE;

    // When: MemberVisibility.fromModifiers(modifiers) is called
    MemberVisibility result = MemberVisibility.fromModifiers(modifiers);

    // Then: Returns MemberVisibility.PRIVATE
    assertThat(result, is(MemberVisibility.PRIVATE));
  }

  @Test
  public void shouldReturnPackagePrivateForNoAccessModifier() {
    // Given: Modifiers with no access modifier bits set (e.g., Modifier.STATIC)
    int modifiers = Modifier.STATIC;

    // When: MemberVisibility.fromModifiers(modifiers) is called
    MemberVisibility result = MemberVisibility.fromModifiers(modifiers);

    // Then: Returns MemberVisibility.PACKAGE_PRIVATE
    assertThat(result, is(MemberVisibility.PACKAGE_PRIVATE));
  }

  @Test
  public void shouldReturnPackagePrivateForZeroModifiers() {
    // Given: Modifiers value of 0
    // When: MemberVisibility.fromModifiers(0) is called
    MemberVisibility result = MemberVisibility.fromModifiers(0);

    // Then: Returns MemberVisibility.PACKAGE_PRIVATE
    assertThat(result, is(MemberVisibility.PACKAGE_PRIVATE));
  }

  @Test
  public void shouldHandleCombinedModifiers() {
    // Given: Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL
    int modifiers = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;

    // When: MemberVisibility.fromModifiers(modifiers) is called
    MemberVisibility result = MemberVisibility.fromModifiers(modifiers);

    // Then: Returns MemberVisibility.PUBLIC
    assertThat(result, is(MemberVisibility.PUBLIC));
  }

  @Test
  public void shouldParsePublicFromString() {
    // Given: String "PUBLIC"
    // When: MemberVisibility.fromString("PUBLIC") is called
    MemberVisibility result = MemberVisibility.fromString("PUBLIC");

    // Then: Returns MemberVisibility.PUBLIC
    assertThat(result, is(MemberVisibility.PUBLIC));
  }

  @Test
  public void shouldParseCaseInsensitiveFromString() {
    // Given: String "public" (lowercase)
    // When: MemberVisibility.fromString("public") is called
    MemberVisibility result = MemberVisibility.fromString("public");

    // Then: Returns MemberVisibility.PUBLIC
    assertThat(result, is(MemberVisibility.PUBLIC));
  }

  @Test
  public void shouldParseDefaultAsPackagePrivate() {
    // Given: String "DEFAULT"
    // When: MemberVisibility.fromString("DEFAULT") is called
    MemberVisibility result = MemberVisibility.fromString("DEFAULT");

    // Then: Returns MemberVisibility.PACKAGE_PRIVATE
    assertThat(result, is(MemberVisibility.PACKAGE_PRIVATE));
  }

  @Test
  public void shouldParseLowercaseDefaultAsPackagePrivate() {
    // Given: String "default"
    // When: MemberVisibility.fromString("default") is called
    MemberVisibility result = MemberVisibility.fromString("default");

    // Then: Returns MemberVisibility.PACKAGE_PRIVATE
    assertThat(result, is(MemberVisibility.PACKAGE_PRIVATE));
  }

  @Test
  public void shouldReturnNullForAllString() {
    // Given: String "ALL"
    // When: MemberVisibility.fromString("ALL") is called
    MemberVisibility result = MemberVisibility.fromString("ALL");

    // Then: Returns null (sentinel for match-all)
    assertThat(result, is(nullValue()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowForInvalidString() {
    // Given: String "INVALID"
    // When: MemberVisibility.fromString("INVALID") is called
    // Then: Throws IllegalArgumentException
    MemberVisibility.fromString("INVALID");
  }

  @Test
  public void shouldHaveFourValues() {
    // When: MemberVisibility.values() is called
    MemberVisibility[] values = MemberVisibility.values();

    // Then: Returns exactly 4 values: PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE
    assertThat(values.length, is(4));
    assertThat(values[0], is(MemberVisibility.PUBLIC));
    assertThat(values[1], is(MemberVisibility.PROTECTED));
    assertThat(values[2], is(MemberVisibility.PACKAGE_PRIVATE));
    assertThat(values[3], is(MemberVisibility.PRIVATE));
  }
}
