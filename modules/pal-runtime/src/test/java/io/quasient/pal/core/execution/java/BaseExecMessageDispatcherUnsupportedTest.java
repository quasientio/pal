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

import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;

public class BaseExecMessageDispatcherUnsupportedTest {

  static class MinimalDispatcher extends BaseExecMessageDispatcher {
    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt, Object sender, Object target, Object[] args) {
      return new ExecMessage();
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
      return new ExecMessage();
    }

    @Override
    protected Object invoke(ProceedingJoinPoint pjp, Object[] args) {
      return null;
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

  @Test
  public void dispatchIncoming_unsupportedMessageType_throws() {
    MinimalDispatcher d = new MinimalDispatcher();
    MessageBuilder mb = new MessageBuilder(UUID.randomUUID());
    ExecMessage ctorMsg = mb.buildEmptyConstructor(UUID.randomUUID(), "java.lang.String");
    assertThrows(
        IllegalArgumentException.class,
        () -> d.dispatchIncoming(ctorMsg, MessageChannelType.LOG_RPC));
  }
}
