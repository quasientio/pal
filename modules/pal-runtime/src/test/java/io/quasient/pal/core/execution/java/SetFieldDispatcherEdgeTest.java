/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;

public class SetFieldDispatcherEdgeTest {

  static class Sample {
    @SuppressWarnings({"FieldCanBeLocal", "UnusedVariable"})
    private int hidden = 0;

    public int x = 0;
  }

  // Minimal subclass to expose protected methods
  static class TestDispatcher extends SetFieldDispatcher {
    public AccessibleObject load(String className, String fieldName) throws Exception {
      return loadAccessibleObject(className, fieldName);
    }

    public Object valueFrom(Obj valueObject, int ref, AccessibleObject ao) {
      return getValueFromMessage(valueObject, ref, ao);
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      return new ExecMessage();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    protected MessageType getAfterExecMessageType() {
      return MessageType.EXEC_PUT_FIELD_DONE;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_PUT_FIELD;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_PUT_FIELD;
    }
  }

  @Test
  public void loadAccessibleObject_declaredField_whenNonPublicAllowed() throws Exception {
    TestDispatcher d = new TestDispatcher();
    // allow nonpublic via reflection on AbstractDispatcher field
    var f = AbstractDispatcher.class.getDeclaredField("allowNonPublicAccess");
    f.setAccessible(true);
    f.setBoolean(d, true);

    AccessibleObject ao = d.load(Sample.class.getName(), "hidden");
    Field fld = (Field) ao;
    assertThat(fld.getName(), is("hidden"));
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getValueFromMessage_classNotFound_throwsIAE() throws Exception {
    TestDispatcher d = new TestDispatcher();
    Field fld = Sample.class.getDeclaredField("x");
    // Build Obj with bogus class name so Unwrapper.unwrapObject throws ClassNotFoundException
    Obj val = new Obj();
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName("com.no.such.Class");
    val.setClazz(clazz);
    val.setIsNull(false);
    // valueFrom should wrap CNFE into IAE
    assertThrows(IllegalArgumentException.class, () -> d.valueFrom(val, 0, fld));
  }
}
