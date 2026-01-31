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
 * Test fixture class for verifying bounded type parameter signature generation.
 *
 * <p>Used by ClassMetadataSerializerTest to verify that type bounds are correctly included in
 * method signatures.
 */
public class TypeBoundsClass {

  /**
   * Method with bounded type parameter demonstrating single upper bound.
   *
   * @param <T> type parameter bounded by Comparable
   * @param value the value to compare
   * @return the same value
   */
  public <T extends Comparable<T>> T comparableMethod(T value) {
    return value;
  }

  /**
   * Method with multiple type parameter bounds.
   *
   * @param <K> key type bounded by Comparable
   * @param <V> value type with no explicit bound
   * @param key the key
   * @param value the value
   * @return the key
   */
  public <K extends Comparable<? super K>, V> K multiParamMethod(K key, V value) {
    return key;
  }
}
