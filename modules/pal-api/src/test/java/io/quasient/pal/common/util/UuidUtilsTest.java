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
package io.quasient.pal.common.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;
import org.junit.Test;

public class UuidUtilsTest {

  @Test
  public void toBytesFromBytes() {
    UUID uuid = UUID.randomUUID();
    // 8 + 8 bytes
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid);

    assertThat(uuidAsBytes.length, is(16));
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }

  @Test
  public void toBytes() {
    UUID uuid = UUID.randomUUID();
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid.toString());
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }
}
