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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class IdGeneratorsTest {

  private static boolean isBase62(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
      if (!ok) return false;
    }
    return true;
  }

  @Test
  public void fastIdGeneratorNonCrypto_generates22Base62Chars_andMostlyUnique() {
    IdGenerator g = new FastIdGeneratorNonCrypto();
    Set<String> set = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String id = g.nextId();
      assertThat(id.length(), is(22));
      assertTrue(isBase62(id));
      set.add(id);
    }
    // Expect very low/no collisions in 1k samples
    assertThat(set, hasSize(1000));
  }

  @Test
  public void base62UuidGenerator_generates22Base62Chars_andUnique() {
    IdGenerator g = new Base62UuidGenerator();
    Set<String> set = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String id = g.nextId();
      assertThat(id.length(), is(22));
      assertTrue(isBase62(id));
      set.add(id);
    }
    assertThat(set, hasSize(200));
  }
}
