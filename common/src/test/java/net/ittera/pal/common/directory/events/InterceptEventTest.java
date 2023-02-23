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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.UUID;
import net.ittera.pal.common.directory.events.InterceptEvent.Type;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class InterceptEventTest {

  private Type type;
  private String interceptPath;
  private UUID peerUUID;
  private UUID interceptUUID;
  private InterceptRequest interceptRequest;
  InterceptEvent interceptEvent;

  @Before
  public void setUp() {
    type = Type.INTERCEPT_ADDED;
    interceptPath = "/a/mysterious/path";
    peerUUID = UUID.randomUUID();
    interceptUUID = UUID.randomUUID();
    interceptRequest = createInterceptRequest();
    interceptEvent =
        new InterceptEvent(type, interceptPath, peerUUID, interceptUUID, interceptRequest);
  }

  private InterceptRequest createInterceptRequest() {
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
    EqualsVerifier.forClass(InterceptEvent.class).usingGetClass().verify();
  }

  @Test
  public void getType() {
    assertEquals(type, interceptEvent.getType());
  }

  @Test
  public void getInterceptPath() {
    assertEquals(interceptPath, interceptEvent.getInterceptPath());
  }

  @Test
  public void getPeerUUID() {
    assertEquals(peerUUID, interceptEvent.getPeerUUID());
  }

  @Test
  public void getInterceptUUID() {
    assertEquals(interceptUUID, interceptEvent.getInterceptUUID());
  }

  @Test
  public void getInterceptRequest() {
    assertEquals(interceptRequest, interceptEvent.getInterceptRequest());
  }

  @Test
  public void testToString() {
    assertThat(
        interceptEvent.toString(),
        is(
            "InterceptEvent{"
                + "type="
                + type
                + ", interceptPath='"
                + interceptPath
                + '\''
                + ", peerUUID="
                + peerUUID
                + ", interceptUUID="
                + interceptUUID
                + ", interceptRequest='"
                + interceptRequest
                + '\''
                + '}'));
  }
}
