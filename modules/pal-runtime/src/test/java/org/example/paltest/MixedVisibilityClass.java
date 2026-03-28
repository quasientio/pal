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
package org.example.paltest;

/**
 * Test fixture with members at all four Java visibility levels. Used by {@code
 * ClassMetadataSerializerRpcPolicyTest} to verify visibility-based metadata filtering.
 */
@SuppressWarnings({"UnusedVariable", "UnusedMethod"})
public class MixedVisibilityClass {

  /** A public field. */
  public int publicField;

  /** A protected field. */
  protected int protectedField;

  /** A package-private field. */
  int packageField;

  /** A private field. */
  private int privateField;

  /** Public no-arg constructor. */
  public MixedVisibilityClass() {}

  /** Package-private constructor with an int parameter. */
  MixedVisibilityClass(int value) {
    this.privateField = value;
  }

  /** A public method. */
  public void publicMethod() {}

  /** A protected method. */
  protected void protectedMethod() {}

  /** A package-private method. */
  void packageMethod() {}

  /** A private method. */
  private void privateMethod() {}
}
