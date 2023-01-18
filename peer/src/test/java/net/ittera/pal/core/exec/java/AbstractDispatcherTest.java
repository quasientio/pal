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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectStore;
import net.ittera.pal.common.objects.ObjectStore;
import net.ittera.pal.core.SessionStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.Before;
import org.mockito.AdditionalAnswers;

public abstract class AbstractDispatcherTest {

  protected UUID peerUuid = UUID.randomUUID();

  protected ObjectStore objectStore = new ConcurrentHashMapObjectStore();

  protected SessionStore sessionStore = new SessionStore();

  protected MessageBuilder messageBuilder = new MessageBuilder();

  protected DispatcherConnector dispatcherConnector;

  protected AbstractDispatcherTest() {
    // set up mock dispatcher so it returns always the sent message (for ExecMessages)
    dispatcherConnector = mock(DispatcherConnector.class);
    when(dispatcherConnector.sendExecMessage(any(), any()))
        .then(AdditionalAnswers.returnsFirstArg());
  }

  private void verifyDispatcherConnectorSendExecMessageCalledTimes(int n) {
    verify(dispatcherConnector, times(n)).sendExecMessage(any(), any());
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledTwice() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(2);
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledOnce() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(1);
  }

  protected String[] toNames(Class[] types) {
    return Arrays.stream(types)
        .map(p -> p.getName())
        .collect(toList())
        .toArray(new String[types.length]);
  }

  @Before
  public void clearStuff() {
    objectStore.clear();
    assertThat(objectStore.size(), is(0L));
  }
}
