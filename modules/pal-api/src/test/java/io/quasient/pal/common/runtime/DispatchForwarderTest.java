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
package io.quasient.pal.common.runtime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.BeforeClass;
import org.junit.Test;

/** Unit tests for {@link DispatchForwarder}. */
public class DispatchForwarderTest {

  /** A dummy ProxyDispatcher implementation used to verify forwarding behavior. */
  private static class DummyDispatcher implements ProxyDispatcher {

    /** Return value to be used by the dispatcher methods. */
    Object ret;

    /** Flag to trigger exception on getStatic. */
    boolean throwOnGetStatic;

    /** Flag to trigger exception on getObject. */
    boolean throwOnGetObject;

    /** Flag to trigger exception on putStatic. */
    boolean throwOnPutStatic;

    /** Flag to trigger exception on putField. */
    boolean throwOnPutField;

    @Override
    public Object constructor(ProceedingJoinPoint pjp) {
      return ret;
    }

    @Override
    public void voidInstanceMethod(ProceedingJoinPoint pjp) {}

    @Override
    public void voidClassMethod(ProceedingJoinPoint pjp) {}

    @Override
    public Object nonVoidInstanceMethod(ProceedingJoinPoint pjp) {
      return ret;
    }

    @Override
    public Object nonVoidClassMethod(ProceedingJoinPoint pjp) {
      return ret;
    }

    @Override
    public Object getStatic(ProceedingJoinPoint pjp) throws Throwable {
      if (throwOnGetStatic) throw new Exception("x");
      return ret;
    }

    @Override
    public Object getObject(ProceedingJoinPoint pjp) throws Throwable {
      if (throwOnGetObject) throw new Exception("x");
      return ret;
    }

    @Override
    public void putStatic(ProceedingJoinPoint pjp) throws Throwable {
      if (throwOnPutStatic) throw new Exception("x");
    }

    @Override
    public void putField(ProceedingJoinPoint pjp) throws Throwable {
      if (throwOnPutField) throw new Exception("x");
    }
  }

  /** Injects a DummyDispatcher into the DispatchForwarder. */
  @BeforeClass
  public static void injectDummyDispatcher() throws Exception {
    Field f = DispatchForwarder.class.getDeclaredField("dispatcher");
    f.setAccessible(true);
    DummyDispatcher dd = new DummyDispatcher();
    dd.ret = "R";
    f.set(null, dd);
  }

  /** Tests that constructor and non-void method calls are forwarded correctly. */
  @Test
  public void forwards_constructor_and_nonVoid_calls() throws Throwable {
    assertThat(DispatchForwarder.constructor(null), is("R"));
    assertThat(DispatchForwarder.nonVoidInstanceMethod(null), is("R"));
    assertThat(DispatchForwarder.nonVoidClassMethod(null), is("R"));
  }

  /** Tests that void method calls are forwarded correctly. */
  @Test
  public void forwards_void_calls() throws Throwable {
    DispatchForwarder.voidInstanceMethod(null);
    DispatchForwarder.voidClassMethod(null);
  }

  /** Tests that checked exceptions from field operations are wrapped in RuntimeException. */
  @Test
  public void wrappers_convert_checked_exceptions_to_Runtime_for_field_ops() throws Exception {
    Field f = DispatchForwarder.class.getDeclaredField("dispatcher");
    f.setAccessible(true);
    DummyDispatcher dd = (DummyDispatcher) f.get(null);
    dd.throwOnGetStatic = true;
    dd.throwOnGetObject = true;
    dd.throwOnPutStatic = true;
    dd.throwOnPutField = true;

    assertThrows(RuntimeException.class, () -> DispatchForwarder.getStatic(null));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.getObject(null));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.putStatic(null));
    assertThrows(RuntimeException.class, () -> DispatchForwarder.putField(null));
  }
}
