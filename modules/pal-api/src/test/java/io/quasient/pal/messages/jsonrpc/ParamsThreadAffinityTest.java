/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
