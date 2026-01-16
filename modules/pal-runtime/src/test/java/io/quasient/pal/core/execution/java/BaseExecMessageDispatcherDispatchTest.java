/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;
import org.mockito.Mockito;

public class BaseExecMessageDispatcherDispatchTest {

  static class MinimalOk extends BaseExecMessageDispatcher {
    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt, Object sender, Object target, Object[] args) {
      try {
        Constructor<?> ctor = String.class.getConstructor();
        return new MessageBuilder(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .buildReturnValue("", ctor, null, false, null);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      try {
        Constructor<?> ctor = String.class.getConstructor();
        return new MessageBuilder(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .buildReturnValue("", ctor, null, false, null);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected Object invokeIncoming(
        AccessibleObject accessibleObject,
        Object target,
        List<MessageArgument> args,
        Object value) {
      return null;
    }

    @Override
    protected boolean returnsVoid(AccessibleObject accessibleObject) {
      return false;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return false;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Parameter> getParameterList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }

  static class MinimalThrows extends MinimalOk {
    @Override
    protected Object invoke(ProceedingJoinPoint pjp, Object[] args) throws Throwable {
      throw new InvocationTargetException(new IllegalStateException("boom"));
    }
  }

  private static void setRunOptions(AbstractDispatcher d, Set<RunOptions> ro) throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("runOptions");
    f.setAccessible(true);
    f.set(d, ro);
  }

  @Test
  public void dispatch_returnsProceedValue_withoutWalOrPub() throws Throwable {
    MinimalOk d = new MinimalOk();
    setRunOptions(d, EnumSet.noneOf(RunOptions.class));

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart sp = mock(JoinPoint.StaticPart.class);
    Mockito.when(pjp.getThis()).thenReturn(this);
    Mockito.when(pjp.getTarget()).thenReturn(this);
    Mockito.when(pjp.getArgs()).thenReturn(new Object[] {"x"});
    Mockito.when(pjp.getStaticPart()).thenReturn(sp);
    // Stub pjp.proceed(Object[]) to return "ok"
    Mockito.when(pjp.proceed(Mockito.any(Object[].class))).thenReturn("ok");

    Object out = d.dispatch(pjp);
    assertThat(out, is("ok"));
  }

  @Test(expected = IllegalStateException.class)
  public void dispatch_wrapsInvocationTargetException_andRethrowsCause() throws Throwable {
    MinimalThrows d = new MinimalThrows();
    setRunOptions(d, EnumSet.noneOf(RunOptions.class));
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    Mockito.when(pjp.getThis()).thenReturn(this);
    Mockito.when(pjp.getTarget()).thenReturn(this);
    Mockito.when(pjp.getArgs()).thenReturn(new Object[] {});
    Mockito.when(pjp.getStaticPart()).thenReturn(mock(JoinPoint.StaticPart.class));
    d.dispatch(pjp);
  }

  // NOTE: A more realistic WAL/BEFORE/AFTER path is exercised via concrete dispatcher tests.
}
