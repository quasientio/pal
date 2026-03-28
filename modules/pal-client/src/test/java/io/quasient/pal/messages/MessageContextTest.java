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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class MessageContextTest {

  @Test
  public void fieldsAndToString() {
    MessageContext mc = new MessageContext(42L, 1, 1000L, "topic-x", "log-1");
    assertThat(mc.offset(), is(42L));
    assertThat(mc.partition(), is(1));
    assertThat(mc.timestamp(), is(1000L));
    assertThat(mc.topic(), is("topic-x"));
    assertThat(mc.logId(), is("log-1"));
    String s = mc.toString();
    assertThat(s, containsString("offset=42"));
    assertThat(s, containsString("topic='topic-x'"));
  }
}
