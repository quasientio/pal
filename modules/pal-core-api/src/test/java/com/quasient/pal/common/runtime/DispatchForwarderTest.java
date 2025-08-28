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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;

public class DispatchForwarderTest {

  private static ProxyDispatcher mockedDispatcher;
  private static ProceedingJoinPoint proceedingJoinPoint;

  @Before
  public void setUp() throws Throwable {
    mockedDispatcher = mock(ProxyDispatcher.class);
    proceedingJoinPoint = mock(ProceedingJoinPoint.class);

    Guice.createInjector(
        binder -> {
          binder.bind(ProxyDispatcher.class).toInstance(mockedDispatcher);
          binder.requestStaticInjection(DispatchForwarder.class);
        });
  }

  @Test
  public void constructor() throws Throwable {
    Object valueToReturn = "constructor OK";
    when(mockedDispatcher.constructor(any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.constructor(proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).constructor(any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void voidInstanceMethod() throws Throwable {
    DispatchForwarder.voidInstanceMethod(proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).voidInstanceMethod(any(), any());
  }

  @Test
  public void voidClassMethod() throws Throwable {
    DispatchForwarder.voidClassMethod(proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).voidClassMethod(any(), any());
  }

  @Test
  public void nonVoidInstanceMethod() throws Throwable {
    Object valueToReturn = "instance method OK";
    when(mockedDispatcher.nonVoidInstanceMethod(any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.nonVoidInstanceMethod(proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).nonVoidInstanceMethod(any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void nonVoidClassMethod() throws Throwable {
    Object valueToReturn = "class method OK";
    when(mockedDispatcher.nonVoidClassMethod(any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.nonVoidClassMethod(proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).nonVoidClassMethod(any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getStatic() throws Throwable {
    Object valueToReturn = "get static OK";
    when(mockedDispatcher.getStatic(any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getStatic(proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).getStatic(any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getObject() throws Throwable {
    Object valueToReturn = "get object OK";
    when(mockedDispatcher.getObject(any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getObject(proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).getObject(any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void putStatic() throws Throwable {
    DispatchForwarder.putStatic(proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).putStatic(any(), any());
  }

  @Test
  public void putField() throws Throwable {
    DispatchForwarder.putField(proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).putField(any(), any());
  }
}
