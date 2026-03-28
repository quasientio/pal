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
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.ReturnValue;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class ExecMessageUtilsMoreTest {

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder b;

  @Before
  public void setUp() {
    b = new MessageBuilder(peerId);
  }

  static class V {
    @SuppressWarnings("unused")
    void v() {}
  }

  @Test
  public void getClassname_returnValue_voidReturnsVoid() throws Exception {
    Method mv = V.class.getDeclaredMethod("v");
    ExecMessage em =
        b.buildReturnValue(null, mv, ObjectRef.randomRef(), true, UUID.randomUUID().toString());
    assertThat(ExecMessageUtils.getClassname(em), is("void"));
  }

  @Test
  public void getFromExecutableName_returnValue_noFrom_returnsNull() {
    ExecMessage em = new ExecMessage().withReturnValue(new ReturnValue().withIsVoid(true));
    assertNull(ExecMessageUtils.getFromExecutableName(em));
  }

  @Test
  public void getFromExecutableClassName_returnValue_returnsDeclaringClass() throws Exception {
    Method mv = V.class.getDeclaredMethod("v");
    ExecMessage em =
        b.buildReturnValue(null, mv, ObjectRef.randomRef(), true, UUID.randomUUID().toString());
    assertEquals(V.class.getName(), ExecMessageUtils.getFromExecutableClassName(em));
  }

  @Test
  public void getFromExecutableClassName_raisedThrowable_returnsDeclaringClass() throws Exception {
    Method mv = V.class.getDeclaredMethod("v");
    ExecMessage em = b.buildAccessibleObjectThrowable(peerId, mv, new RuntimeException("x"), "r");
    assertEquals(V.class.getName(), ExecMessageUtils.getFromExecutableClassName(em));
  }

  @Test
  public void getMessageTypeOf_unknown_throws() {
    ExecMessage em = new ExecMessage();
    assertThrows(IllegalArgumentException.class, () -> ExecMessageUtils.getMessageTypeOf(em));
  }
}
