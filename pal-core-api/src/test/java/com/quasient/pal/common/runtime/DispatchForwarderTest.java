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

package com.quasient.pal.common.runtime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class DispatchForwarderTest {

  private static ProxyDispatcher mockedDispatcher;

  @Before
  public void setUp() throws Throwable {
    mockedDispatcher = mock(ProxyDispatcher.class);
    DispatchForwarder.setDispatcher(mockedDispatcher);
  }

  @Test
  public void constructor() throws Throwable {
    Object valueToReturn = "constructor OK";
    when(mockedDispatcher.constructor(any(), any(), any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.constructor(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).constructor(any(), any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void voidInstanceMethod() throws Throwable {
    DispatchForwarder.voidInstanceMethod(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).voidInstanceMethod(any(), any(), any(), any());
  }

  @Test
  public void voidClassMethod() throws Throwable {
    DispatchForwarder.voidClassMethod(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).voidClassMethod(any(), any(), any(), any());
  }

  @Test
  public void nonVoidInstanceMethod() throws Throwable {
    Object valueToReturn = "instance method OK";
    when(mockedDispatcher.nonVoidInstanceMethod(any(), any(), any(), any()))
        .thenReturn(valueToReturn);
    Object ret =
        DispatchForwarder.nonVoidInstanceMethod(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).nonVoidInstanceMethod(any(), any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void nonVoidClassMethod() throws Throwable {
    Object valueToReturn = "class method OK";
    when(mockedDispatcher.nonVoidClassMethod(any(), any(), any(), any())).thenReturn(valueToReturn);
    Object ret =
        DispatchForwarder.nonVoidClassMethod(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).nonVoidClassMethod(any(), any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getStatic() throws Throwable {
    Object valueToReturn = "get static OK";
    when(mockedDispatcher.getStatic(any(), any(), any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getStatic(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).getStatic(any(), any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void getObject() throws Throwable {
    Object valueToReturn = "get object OK";
    when(mockedDispatcher.getObject(any(), any(), any(), any())).thenReturn(valueToReturn);
    Object ret = DispatchForwarder.getObject(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).getObject(any(), any(), any(), any());
    assertThat(ret, is(valueToReturn));
  }

  @Test
  public void putStatic() throws Throwable {
    DispatchForwarder.putStatic(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).putStatic(any(), any(), any(), any());
  }

  @Test
  public void putField() throws Throwable {
    DispatchForwarder.putField(mock(Context.class), null, null, new Object[0]);
    verify(mockedDispatcher).putField(any(), any(), any(), any());
  }
}
