/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.runtime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import com.quasient.pal.common.weave.Proceed;
import com.quasient.pal.common.weave.VoidProceed;
import java.lang.reflect.Field;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.BeforeClass;
import org.junit.Test;

public class DispatchForwarderTest {

  private static class DummyDispatcher implements ProxyDispatcher {
    Object ret;
    boolean throwOnGetStatic;
    boolean throwOnGetObject;
    boolean throwOnPutStatic;
    boolean throwOnPutField;

    @Override
    public Object constructor(ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable {
      return ret;
    }

    @Override
    public void voidInstanceMethod(ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable {}

    @Override
    public void voidClassMethod(ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable {}

    @Override
    public Object nonVoidInstanceMethod(ProceedingJoinPoint pjp, Proceed<Object> proceed)
        throws Throwable {
      return ret;
    }

    @Override
    public Object nonVoidClassMethod(ProceedingJoinPoint pjp, Proceed<Object> proceed)
        throws Throwable {
      return ret;
    }

    @Override
    public Object getStatic(ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable {
      if (throwOnGetStatic) throw new Exception("x");
      return ret;
    }

    @Override
    public Object getObject(ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable {
      if (throwOnGetObject) throw new Exception("x");
      return ret;
    }

    @Override
    public void putStatic(ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable {
      if (throwOnPutStatic) throw new Exception("x");
    }

    @Override
    public void putField(ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable {
      if (throwOnPutField) throw new Exception("x");
    }
  }

  @BeforeClass
  public static void injectDummyDispatcher() throws Exception {
    Field f = DispatchForwarder.class.getDeclaredField("dispatcher");
    f.setAccessible(true);
    DummyDispatcher dd = new DummyDispatcher();
    dd.ret = "R";
    f.set(null, dd);
  }

  @Test
  public void forwards_constructor_and_nonVoid_calls() throws Throwable {
    assertThat(DispatchForwarder.constructor(null, () -> "X"), is("R"));
    assertThat(DispatchForwarder.nonVoidInstanceMethod(null, () -> "X"), is("R"));
    assertThat(DispatchForwarder.nonVoidClassMethod(null, () -> "X"), is("R"));
  }

  @Test
  public void forwards_void_calls() throws Throwable {
    DispatchForwarder.voidInstanceMethod(null, () -> {});
    DispatchForwarder.voidClassMethod(null, () -> {});
  }

  @Test
  public void wrappers_convert_checked_exceptions_to_Runtime_for_field_ops() throws Exception {
    Field f = DispatchForwarder.class.getDeclaredField("dispatcher");
    f.setAccessible(true);
    DummyDispatcher dd = (DummyDispatcher) f.get(null);
    dd.throwOnGetStatic = true;
    dd.throwOnGetObject = true;
    dd.throwOnPutStatic = true;
    dd.throwOnPutField = true;

    assertThrows(RuntimeException.class, () -> DispatchForwarder.getStatic(null, () -> null));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.getObject(null, () -> null));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.putStatic(null, () -> {}));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.putField(null, () -> {}));
  }
}
