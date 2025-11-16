/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.messages.colfer.InterceptMessage;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Unit tests for {@link InterceptCheckResult}. */
public class InterceptCheckResultTest {

  @Test
  public void noIntercepts_returnsFalseForAll() {
    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), Collections.emptyList());

    assertThat(result.hasRemoteIntercepts(), is(false));
    assertThat(result.hasLocalIntercepts(), is(false));
    assertThat(result.needsExecMessage(), is(false));
    assertThat(result.getRemoteIntercepts(), is(empty()));
    assertThat(result.getLocalIntercepts(), is(empty()));
  }

  @Test
  public void onlyRemoteIntercepts_returnsCorrectFlags() {
    InterceptMessage remoteIntercept = new InterceptMessage();
    List<InterceptMessage> remoteIntercepts = List.of(remoteIntercept);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.hasLocalIntercepts(), is(false));
    assertThat(result.needsExecMessage(), is(true)); // remote intercepts need ExecMessage
    assertThat(result.getRemoteIntercepts().size(), is(1));
    assertThat(result.getLocalIntercepts(), is(empty()));
  }

  @Test
  public void onlyLocalIntercepts_returnsCorrectFlags() {
    InterceptMessage localIntercept = new InterceptMessage();
    List<InterceptMessage> localIntercepts = List.of(localIntercept);

    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), localIntercepts);

    assertThat(result.hasRemoteIntercepts(), is(false));
    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.needsExecMessage(), is(false)); // local intercepts don't need ExecMessage
    assertThat(result.getRemoteIntercepts(), is(empty()));
    assertThat(result.getLocalIntercepts().size(), is(1));
  }

  @Test
  public void bothRemoteAndLocalIntercepts_returnsCorrectFlags() {
    InterceptMessage remoteIntercept = new InterceptMessage();
    InterceptMessage localIntercept = new InterceptMessage();
    List<InterceptMessage> remoteIntercepts = List.of(remoteIntercept);
    List<InterceptMessage> localIntercepts = List.of(localIntercept);

    InterceptCheckResult result = new InterceptCheckResult(remoteIntercepts, localIntercepts);

    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.needsExecMessage(), is(true)); // remote intercepts need ExecMessage
    assertThat(result.getRemoteIntercepts().size(), is(1));
    assertThat(result.getLocalIntercepts().size(), is(1));
  }

  @Test
  public void multipleRemoteIntercepts_allReturned() {
    InterceptMessage intercept1 = new InterceptMessage();
    InterceptMessage intercept2 = new InterceptMessage();
    InterceptMessage intercept3 = new InterceptMessage();
    List<InterceptMessage> remoteIntercepts = List.of(intercept1, intercept2, intercept3);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.needsExecMessage(), is(true));
    assertThat(result.getRemoteIntercepts().size(), is(3));
  }

  @Test
  public void multipleLocalIntercepts_allReturned() {
    InterceptMessage intercept1 = new InterceptMessage();
    InterceptMessage intercept2 = new InterceptMessage();
    List<InterceptMessage> localIntercepts = List.of(intercept1, intercept2);

    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), localIntercepts);

    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.needsExecMessage(), is(false));
    assertThat(result.getLocalIntercepts().size(), is(2));
  }
}
