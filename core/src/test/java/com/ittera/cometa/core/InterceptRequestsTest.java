package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.ittera.cometa.core.exec.DuplicateInterceptException;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptKeyMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

public class InterceptRequestsTest {

  @Test
  public void getMatchingIntercepts() throws Exception {
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    // register request
    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now try matching ExecMessage
    ExecMessage execMessage =
        msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.util.ArrayList");
    InterceptKeyMessage execKeyMessage = msgBuilder.buildInterceptKey(execMessage);
    assertThat(interceptRequests.getMatchingIntercepts(execKeyMessage), is(notNullValue()));
    assertThat(interceptRequests.getMatchingIntercepts(execKeyMessage).size(), is(1));
  }

  @Test
  public void registerInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptRequests interceptRequests = new InterceptRequests();
    // pre-assertion
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(0));

    // register and verify
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));
  }

  @Test
  public void registerDupInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now try to re-register
    try {
      interceptRequests.registerInterceptRequest(interceptMessage);
      fail("Should have raised DuplicateInterceptException");
    } catch (DuplicateInterceptException e) {
      // ok
      assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));
    }
  }

  @Test
  public void unregisterInterceptRequest() throws Exception {
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();

    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    // register request
    InterceptRequests interceptRequests = new InterceptRequests();
    interceptRequests.registerInterceptRequest(interceptMessage);
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(1));

    // now unregister
    interceptRequests.unregisterInterceptRequest(interceptMessage.getMessageUuid());
    assertThat(interceptRequests.getRegisteredRequestsSize(), is(0));
  }
}
