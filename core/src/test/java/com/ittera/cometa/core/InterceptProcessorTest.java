package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class App {
  @com.ittera.cometa.common.lang.annotation.Before(
      clazz = "java.io.PrintStream",
      method = "println")
  public static void printlnAndStop(String line) {}
}

public class InterceptProcessorTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private DispatcherConnector dispatcherConnector;
  private InterceptProcessor interceptProcessor;
  private final UUID peerUuid = UUID.randomUUID();

  private List<InterceptMessage> requests;

  @Before
  public void setUp() throws Exception {
    requests = new ArrayList<>();
    // set up mock dispatcher so it returns always 0 (0 == OK)
    dispatcherConnector = mock(DispatcherConnector.class);
    when(dispatcherConnector.sendOutInterceptRequest(any()))
        .thenAnswer(
            // save produced request for verification
            (Answer)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  InterceptMessage request = (InterceptMessage) args[0];
                  requests.add(request);
                  return 0;
                });
    interceptProcessor = new InterceptProcessor(peerUuid, msgBuilder, dispatcherConnector);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void processClassWithBeforeAnnotation() {
    interceptProcessor.process(App.class);

    // ensure dispatcherConnector called
    verify(dispatcherConnector, times(1)).sendOutInterceptRequest(any());

    // verify request contents
    assertThat(requests.size(), is(1));
    InterceptMessage interceptMessage = requests.get(0);
    assertThat(interceptMessage.getType(), is(InterceptType.BEFORE));
    assertThat(interceptMessage.getMethod().getName(), is("println"));
    assertThat(interceptMessage.getMethod().getParameterTypeCount(), is(1));
    assertThat(interceptMessage.getMethod().getParameterTypeList().get(0), is("java.lang.String"));
    assertThat(interceptMessage.getClazz(), is("java.io.PrintStream"));
    assertThat(interceptMessage.getCallbackClass(), is("com.ittera.cometa.core.App"));
    assertThat(interceptMessage.getCallbackMethod(), is("printlnAndStop"));
  }
}
