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
import net.ittera.pal.common.ConcurrentHashMapObjectStore;
import net.ittera.pal.common.ObjectStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import org.junit.Before;
import org.mockito.AdditionalAnswers;

public abstract class AbstractDispatcherTest {

  protected UUID peerUuid = UUID.randomUUID();

  protected ObjectStore objectStore = new ConcurrentHashMapObjectStore();

  protected MessageBuilder messageBuilder = new ProtobufMessageBuilder();

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
