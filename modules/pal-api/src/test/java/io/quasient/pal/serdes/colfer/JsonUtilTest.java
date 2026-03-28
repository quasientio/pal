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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class JsonUtilTest {

  static class Bean {
    public String a = "x";
  }

  @Test
  public void toJson_and_fromJson_roundTrip() {
    Bean b = new Bean();
    String json = JsonUtil.toJson(b);
    assertThat(json, containsString("\"a\":\"x\""));
    Bean parsed = JsonUtil.fromJson(json, Bean.class);
    assertEquals("x", parsed.a);
  }

  @Test
  public void fromJson_throwsOnInvalid() {
    assertThrows(RuntimeException.class, () -> JsonUtil.fromJson("{oops:", Bean.class));
  }
}
