/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
