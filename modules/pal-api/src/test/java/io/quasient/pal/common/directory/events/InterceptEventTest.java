/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.directory.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import java.util.UUID;
import org.junit.Test;

public class InterceptEventTest {

  private InterceptRequest<?> createInterceptRequest() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptType type = InterceptType.BEFORE;
    String clazz = "com.dummy.Class";
    String callbackClass = "com.dummy.CallbackClass";
    String callbackMethod = "MyCallback";
    InterceptableFieldOp interceptableFieldOp =
        new InterceptableFieldOp("myField", FieldOpType.GET);
    return new InterceptRequest<>(
        uuid, peer, type, clazz, callbackClass, callbackMethod, interceptableFieldOp);
  }

  @Test
  public void equalsContract() {
    InterceptRequest<?> req = createInterceptRequest();
    InterceptEvent a =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "id-1",
            req);
    InterceptEvent b =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "id-1",
            req);
    InterceptEvent c =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "id-1",
            req);
    InterceptEvent different =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "id-2",
            req);

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }

  @Test
  public void getType() {
    InterceptEvent.Type type = InterceptEvent.Type.INTERCEPT_ADDED;
    InterceptEvent interceptEvent =
        new InterceptEvent(
            type,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            createInterceptRequest());

    assertEquals(type, interceptEvent.type());
  }

  @SuppressWarnings("DataFlowIssue")
  @Test(expected = NullPointerException.class)
  public void nullType_exception() {
    new InterceptEvent(
        null,
        "/a/mysterious/path/ahead",
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        createInterceptRequest());
  }

  @Test
  public void getInterceptPath() {
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            createInterceptRequest());

    assertEquals("/a/mysterious/path/ahead", interceptEvent.interceptPath());
  }

  @SuppressWarnings("DataFlowIssue")
  @Test(expected = NullPointerException.class)
  public void nullPath_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        null,
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        createInterceptRequest());
  }

  @Test(expected = IllegalArgumentException.class)
  public void blankPath_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "",
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        createInterceptRequest());
  }

  @Test
  public void getPeerUuid() {
    UUID peerUuid = UUID.randomUUID();
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            peerUuid,
            UUID.randomUUID().toString(),
            createInterceptRequest());

    assertEquals(peerUuid, interceptEvent.peerUuid());
  }

  @SuppressWarnings("DataFlowIssue")
  @Test(expected = NullPointerException.class)
  public void nullPeerUuid_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "/a/mysterious/path/ahead",
        null,
        UUID.randomUUID().toString(),
        createInterceptRequest());
  }

  @Test
  public void getInterceptUuid() {
    String interceptId = UUID.randomUUID().toString();
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            interceptId,
            createInterceptRequest());

    assertEquals(interceptId, interceptEvent.interceptId());
  }

  @SuppressWarnings("DataFlowIssue")
  @Test(expected = NullPointerException.class)
  public void nullInterceptUuid_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "/a/mysterious/path/ahead",
        UUID.randomUUID(),
        null,
        createInterceptRequest());
  }

  @Test
  public void getInterceptRequest() {
    InterceptRequest<?> interceptRequest = createInterceptRequest();
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            interceptRequest);

    assertEquals(interceptRequest, interceptEvent.interceptRequest());
  }

  @Test(expected = NullPointerException.class)
  public void nullInterceptRequest_withInterceptAdded_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "/a/mysterious/path/ahead",
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        null);
  }

  @Test
  public void nullInterceptRequest_withInterceptRemoved_ok() {
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_REMOVED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            null);
    assertThat(interceptEvent.interceptRequest(), is(nullValue()));
  }

  @Test
  public void testToString() {
    InterceptEvent.Type type = InterceptEvent.Type.INTERCEPT_ADDED;
    String interceptPath = "/a/mysterious/path/ahead";
    UUID peerUuid = UUID.randomUUID();
    String interceptId = UUID.randomUUID().toString();
    InterceptRequest<?> interceptRequest = createInterceptRequest();

    InterceptEvent interceptEvent =
        new InterceptEvent(type, interceptPath, peerUuid, interceptId, interceptRequest);

    assertThat(
        interceptEvent.toString(),
        is(
            "InterceptEvent["
                + "type="
                + type
                + ", interceptPath="
                + interceptPath
                + ", peerUuid="
                + peerUuid
                + ", interceptId="
                + interceptId
                + ", interceptRequest="
                + interceptRequest
                + ']'));
  }
}
