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
