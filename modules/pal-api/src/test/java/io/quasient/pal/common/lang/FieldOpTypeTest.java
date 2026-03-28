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
package io.quasient.pal.common.lang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FieldOpTypeTest {

  @Test
  public void toByte_and_fromByte_roundTrip() {
    assertThat(FieldOpType.fromByte(FieldOpType.GET.toByte()), is(FieldOpType.GET));
    assertThat(FieldOpType.fromByte(FieldOpType.PUT.toByte()), is(FieldOpType.PUT));
  }

  @Test
  public void fromByte_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> FieldOpType.fromByte((byte) 0));
    assertThrows(IllegalArgumentException.class, () -> FieldOpType.fromByte((byte) 3));
  }
}
