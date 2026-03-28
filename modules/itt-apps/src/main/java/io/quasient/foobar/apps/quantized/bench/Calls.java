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
package io.quasient.foobar.apps.quantized.bench;

import java.util.Arrays;

/** Simple wrapper around misc calls to JDK classes. */
public interface Calls {

  /**
   * Delegates to {@link String#toUpperCase()}
   *
   * @return the argument to upper case
   */
  String toUpperCase(String str);

  /**
   * Delegates to {@link Arrays#sort(double[])}
   *
   * @param doubles an array of doubles
   */
  void sort(double[] doubles);
}
