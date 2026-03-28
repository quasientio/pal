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
package io.quasient.pal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

public class LogMessageHeaderTest {

  @Test
  public void equalsHashCodeAndToString() {
    LogMessageHeader h1 = new LogMessageHeader("k", new byte[] {1, 2});
    LogMessageHeader h2 = new LogMessageHeader("k", new byte[] {1, 2});
    LogMessageHeader h3 = new LogMessageHeader("k", new byte[] {2, 1});

    assertThat(h1, is(h2));
    assertThat(h1.hashCode(), is(h2.hashCode()));
    assertThat(h1.equals(h3), is(false));
    assertThat(h1.toString(), containsString("key='k'"));
    assertThat(h1.key(), is("k"));
    assertThat(h1.value(), is(new byte[] {1, 2}));
  }
}
