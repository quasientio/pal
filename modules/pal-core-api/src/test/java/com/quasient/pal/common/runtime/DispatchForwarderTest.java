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
    DispatchForwarder.setDispatcher(mockedDispatcher);
  }

  @Test
  public void constructor() throws Throwable {
    Object valueToReturn = "constructor OK";
    when(mockedDispatcher.constructor(any(), any(), any())).thenReturn(valueToReturn);
    Object ret =
        DispatchForwarder.constructor(mock(Context.class), proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).constructor(any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void voidInstanceMethod() throws Throwable {
    DispatchForwarder.voidInstanceMethod(mock(Context.class), proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).voidInstanceMethod(any(), any(), any());
  }

  @Test
  public void voidClassMethod() throws Throwable {
    DispatchForwarder.voidClassMethod(mock(Context.class), proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).voidClassMethod(any(), any(), any());
  }

  @Test
  public void nonVoidInstanceMethod() throws Throwable {
    Object valueToReturn = "instance method OK";
    when(mockedDispatcher.nonVoidInstanceMethod(any(), any(), any())).thenReturn(valueToReturn);
    Object ret =
        DispatchForwarder.nonVoidInstanceMethod(
            mock(Context.class), proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).nonVoidInstanceMethod(any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void nonVoidClassMethod() throws Throwable {
    Object valueToReturn = "class method OK";
    when(mockedDispatcher.nonVoidClassMethod(any(), any(), any())).thenReturn(valueToReturn);
    Object ret =
        DispatchForwarder.nonVoidClassMethod(mock(Context.class), proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).nonVoidClassMethod(any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getStatic() throws Throwable {
    Object valueToReturn = "get static OK";
    when(mockedDispatcher.getStatic(any(), any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getStatic(mock(Context.class), proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).getStatic(any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getObject() throws Throwable {
    Object valueToReturn = "get object OK";
    when(mockedDispatcher.getObject(any(), any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getObject(mock(Context.class), proceedingJoinPoint, () -> null);
    verify(mockedDispatcher).getObject(any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void putStatic() throws Throwable {
    DispatchForwarder.putStatic(mock(Context.class), proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).putStatic(any(), any(), any());
  }

  @Test
  public void putField() throws Throwable {
    DispatchForwarder.putField(mock(Context.class), proceedingJoinPoint, () -> {});
    verify(mockedDispatcher).putField(any(), any(), any());
  }
}
