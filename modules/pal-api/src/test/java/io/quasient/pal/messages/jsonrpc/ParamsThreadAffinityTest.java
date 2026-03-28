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
package io.quasient.pal.messages.jsonrpc;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link Params}.
 *
 * <p>Verifies getter/setter, builder support, and inclusion in {@code equals}, {@code hashCode},
 * and {@code toString}.
 */
public class ParamsThreadAffinityTest {

  @Test
  public void threadAffinityGetterSetter() {
    Params params = new Params();
    assertThat(params.getThreadAffinity(), is(nullValue()));

    params.setThreadAffinity("fx-thread");
    assertThat(params.getThreadAffinity(), is("fx-thread"));
  }

  @Test
  public void threadAffinityInEqualsAndHashCode() {
    Params a =
        Params.builder().withType("Foo").withMethod("bar").withThreadAffinity("fx-thread").build();
    Params b = Params.builder().withType("Foo").withMethod("bar").build();

    assertThat(a, is(not(b)));
    assertThat(a.hashCode(), is(not(b.hashCode())));
  }

  @Test
  public void threadAffinityInBuilder() {
    Params params = Params.builder().withType("Foo").withThreadAffinity("fx-thread").build();
    assertThat(params.getThreadAffinity(), is("fx-thread"));
  }

  @Test
  public void threadAffinityInToString() {
    Params params = new Params();
    params.setThreadAffinity("fx-thread");
    assertThat(params.toString(), containsString("threadAffinity"));
    assertThat(params.toString(), containsString("fx-thread"));
  }
}
