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

package net.ittera.pal.core.exec.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.core.exec.java.reflect.ReflectionHelper;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

public abstract class AbstractDispatcherTest {

  protected UUID peerUuid = UUID.randomUUID();

  protected ObjectLookupStore objectLookupStore = new ConcurrentHashMapObjectLookupStore();

  protected MessageBuilder messageBuilder = new MessageBuilder();

  protected ReflectionHelper reflectionHelper =
      new ReflectionHelper(true); // allow access to private, protected and package private methods

  protected ReflectionHelper onlyPublicReflectionHelper = new ReflectionHelper();

  protected DispatcherConnector dispatcherConnector;
  protected Dispatcher dispatcher;
  protected Dispatcher onlyPublicDispatcher;

  protected AbstractDispatcherTest() {}

  private void verifyDispatcherConnectorSendExecMessageCalledTimes(int n) {
    verify(dispatcherConnector, times(n)).sendExecMessage(any(), any());
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledTwice() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(2);
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledOnce() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(1);
  }

  protected String[] toNames(Class<?>[] types) {
    return Arrays.stream(types).map(Class::getName).toList().toArray(new String[types.length]);
  }

  @Before
  public void setUp() {
    assertThat(objectLookupStore.size(), is(0L));
    dispatcherConnector = mock(DispatcherConnector.class);
    when(dispatcherConnector.sendExecMessage(any(), any()))
        .then(AdditionalAnswers.returnsFirstArg());
    when(dispatcherConnector.sendMessageToSessionService(any())).thenReturn(null);
  }

  @After
  public void resetStuff() {
    objectLookupStore.clear();
    assertThat(objectLookupStore.size(), is(0L));
    Mockito.reset(dispatcherConnector);
  }

  // Stubs for accessibility tests
  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_publicAccessibleObject_noException() throws Throwable;

  @SuppressWarnings("unused")
  public abstract void
      dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
          throws Throwable;

  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable;

  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable;
}
