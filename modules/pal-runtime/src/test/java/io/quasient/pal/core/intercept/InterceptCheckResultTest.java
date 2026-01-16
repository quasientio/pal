/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
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

  // ---- AROUND intercept tests ----

  @Test
  public void noAroundIntercepts_hasAroundInterceptsReturnsFalse() {
    InterceptMessage beforeIntercept = new InterceptMessage();
    beforeIntercept.setInterceptType(InterceptType.BEFORE.toByte());
    InterceptMessage afterIntercept = new InterceptMessage();
    afterIntercept.setInterceptType(InterceptType.AFTER.toByte());

    List<InterceptMessage> remoteIntercepts = List.of(beforeIntercept, afterIntercept);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasAroundIntercepts(), is(false));
    assertThat(result.getAroundIntercepts(), is(empty()));
  }

  @Test
  public void onlyAroundIntercept_hasAroundInterceptsReturnsTrue() {
    InterceptMessage aroundIntercept = new InterceptMessage();
    aroundIntercept.setInterceptType(InterceptType.AROUND.toByte());

    List<InterceptMessage> remoteIntercepts = List.of(aroundIntercept);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasAroundIntercepts(), is(true));
    assertThat(result.getAroundIntercepts().size(), is(1));
  }

  @Test
  public void mixedIntercepts_getAroundInterceptsFiltersCorrectly() {
    InterceptMessage beforeIntercept = new InterceptMessage();
    beforeIntercept.setInterceptType(InterceptType.BEFORE.toByte());
    InterceptMessage aroundIntercept1 = new InterceptMessage();
    aroundIntercept1.setInterceptType(InterceptType.AROUND.toByte());
    InterceptMessage afterIntercept = new InterceptMessage();
    afterIntercept.setInterceptType(InterceptType.AFTER.toByte());
    InterceptMessage aroundIntercept2 = new InterceptMessage();
    aroundIntercept2.setInterceptType(InterceptType.AROUND.toByte());

    List<InterceptMessage> remoteIntercepts =
        List.of(beforeIntercept, aroundIntercept1, afterIntercept, aroundIntercept2);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasRemoteIntercepts(), is(true));
    assertThat(result.hasAroundIntercepts(), is(true));
    assertThat(result.getRemoteIntercepts().size(), is(4));
    assertThat(result.getAroundIntercepts().size(), is(2));

    // Verify only AROUND intercepts are returned
    for (InterceptMessage im : result.getAroundIntercepts()) {
      assertThat(InterceptType.fromByte(im.getInterceptType()), is(InterceptType.AROUND));
    }
  }

  @Test
  public void emptyRemoteIntercepts_hasAroundInterceptsReturnsFalse() {
    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), Collections.emptyList());

    assertThat(result.hasAroundIntercepts(), is(false));
    assertThat(result.getAroundIntercepts(), is(empty()));
  }

  @Test
  public void aroundInterceptInLocalList_notConsideredByHasAroundIntercepts() {
    // AROUND intercepts in local list should not be returned by hasAroundIntercepts
    // since that method only checks remoteIntercepts
    InterceptMessage localAround = new InterceptMessage();
    localAround.setInterceptType(InterceptType.AROUND.toByte());

    List<InterceptMessage> localIntercepts = List.of(localAround);

    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), localIntercepts);

    assertThat(result.hasLocalIntercepts(), is(true));
    assertThat(result.hasAroundIntercepts(), is(false)); // only checks remote
    assertThat(result.getAroundIntercepts(), is(empty())); // only returns from remote
  }

  @Test
  public void multipleAroundIntercepts_allReturned() {
    InterceptMessage around1 = new InterceptMessage();
    around1.setInterceptType(InterceptType.AROUND.toByte());
    InterceptMessage around2 = new InterceptMessage();
    around2.setInterceptType(InterceptType.AROUND.toByte());
    InterceptMessage around3 = new InterceptMessage();
    around3.setInterceptType(InterceptType.AROUND.toByte());

    List<InterceptMessage> remoteIntercepts = List.of(around1, around2, around3);

    InterceptCheckResult result =
        new InterceptCheckResult(remoteIntercepts, Collections.emptyList());

    assertThat(result.hasAroundIntercepts(), is(true));
    assertThat(result.getAroundIntercepts().size(), is(3));
  }
}
