/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
