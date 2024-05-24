/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.directory.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.util.UUID;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
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
    InterceptRequest<?> interceptRequest = createInterceptRequest();

    EqualsVerifier.forClass(InterceptEvent.class)
        .withPrefabValues(String.class, "/path/with/four/parts", "/another/path_with/four/parts")
        .withPrefabValues(InterceptRequest.class, interceptRequest, createInterceptRequest())
        .suppress(Warning.NULL_FIELDS)
        .usingGetClass()
        .verify();
  }

  @Test
  public void getType() {
    InterceptEvent.Type type = InterceptEvent.Type.INTERCEPT_ADDED;
    InterceptEvent interceptEvent =
        new InterceptEvent(
            type,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID(),
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
        UUID.randomUUID(),
        createInterceptRequest());
  }

  @Test
  public void getInterceptPath() {
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID(),
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
        UUID.randomUUID(),
        createInterceptRequest());
  }

  @Test(expected = IllegalArgumentException.class)
  public void blankPath_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "",
        UUID.randomUUID(),
        UUID.randomUUID(),
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
            UUID.randomUUID(),
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
        UUID.randomUUID(),
        createInterceptRequest());
  }

  @Test
  public void getInterceptUuid() {
    UUID interceptUuid = UUID.randomUUID();
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            interceptUuid,
            createInterceptRequest());

    assertEquals(interceptUuid, interceptEvent.interceptUuid());
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
            UUID.randomUUID(),
            interceptRequest);

    assertEquals(interceptRequest, interceptEvent.interceptRequest());
  }

  @Test(expected = NullPointerException.class)
  public void nullInterceptRequest_withInterceptAdded_exception() {
    new InterceptEvent(
        InterceptEvent.Type.INTERCEPT_ADDED,
        "/a/mysterious/path/ahead",
        UUID.randomUUID(),
        UUID.randomUUID(),
        null);
  }

  @Test
  public void nullInterceptRequest_withInterceptRemoved_ok() {
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_REMOVED,
            "/a/mysterious/path/ahead",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null);
    assertThat(interceptEvent.interceptRequest(), is(nullValue()));
  }

  @Test
  public void testToString() {
    InterceptEvent.Type type = InterceptEvent.Type.INTERCEPT_ADDED;
    String interceptPath = "/a/mysterious/path/ahead";
    UUID peerUuid = UUID.randomUUID();
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<?> interceptRequest = createInterceptRequest();

    InterceptEvent interceptEvent =
        new InterceptEvent(type, interceptPath, peerUuid, interceptUuid, interceptRequest);

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
                + ", interceptUuid="
                + interceptUuid
                + ", interceptRequest="
                + interceptRequest
                + ']'));
  }
}
