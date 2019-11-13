package com.ittera.cometa.common.znodes;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.common.lang.FieldOpType;
import com.ittera.cometa.common.lang.intercept.InterceptType;
import com.ittera.cometa.common.lang.intercept.InterceptableFieldOp;
import com.ittera.cometa.common.lang.intercept.InterceptableMethodCall;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class InterceptRequestTest {

  @Test
  public void getInterceptable() {

    final UUID reqUuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptType interceptType = InterceptType.BEFORE;
    String clazz = "java.io.PrintStream";
    String callbackClass = "org.package.Callback";
    String callbackMethod = "callMe";
    InterceptableFieldOp interceptable = new InterceptableFieldOp("out", FieldOpType.GET);

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        new InterceptRequest<>(
            reqUuid, peer, interceptType, clazz, callbackClass, callbackMethod, interceptable);

    assertThat(interceptRequest.getInterceptable(), is(interceptable));
  }

  @Test
  public void equals() {
    final UUID reqUuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest1 =
        new InterceptRequest<>(
            reqUuid,
            peer,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableFieldOp("out", FieldOpType.GET));

    InterceptRequest<InterceptableFieldOp> interceptRequest2 =
        new InterceptRequest<>(
            reqUuid,
            peer,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableFieldOp("out", FieldOpType.GET));

    // assert equals
    assertThat(interceptRequest1, is(not(sameInstance(interceptRequest2))));
    assertThat(interceptRequest1, is(interceptRequest2));

    // different reqUuid
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    UUID.randomUUID(),
                    peer,
                    InterceptType.BEFORE,
                    "java.io.PrintStream",
                    "org.package.Callback",
                    "callMe",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different peerUuid
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    UUID.randomUUID(),
                    InterceptType.BEFORE,
                    "java.io.PrintStream",
                    "org.package.Callback",
                    "callMe",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different interceptType
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    peer,
                    InterceptType.BEFORE_ASYNC,
                    "java.io.PrintStream",
                    "org.package.Callback",
                    "callMe",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different class
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    peer,
                    InterceptType.BEFORE,
                    "java.util.List",
                    "org.package.Callback",
                    "callMe",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different callback class
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    peer,
                    InterceptType.BEFORE,
                    "java.io.PrintStream",
                    "org.package.AnotherCallback",
                    "callMe",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different callback method
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    peer,
                    InterceptType.BEFORE,
                    "java.io.PrintStream",
                    "org.package.Callback",
                    "listener",
                    new InterceptableFieldOp("out", FieldOpType.GET)))));

    // different interceptable
    assertThat(
        interceptRequest1,
        is(
            not(
                new InterceptRequest<>(
                    reqUuid,
                    peer,
                    InterceptType.BEFORE,
                    "java.io.PrintStream",
                    "org.package.Callback",
                    "callMe",
                    new InterceptableMethodCall(
                        "someMethod", Collections.singletonList("java.lang.String"))))));
  }

  @Test
  public void toAndFromBytes() {
    List<InterceptRequest> interceptRequests = new ArrayList<>();
    interceptRequests.add(
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableFieldOp("out", FieldOpType.GET)));
    new InterceptRequest<>(
        UUID.randomUUID(),
        UUID.randomUUID(),
        InterceptType.BEFORE,
        "java.io.PrintStream",
        "org.package.Callback",
        "callMe",
        new InterceptableMethodCall("println", null));
    interceptRequests.add(
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", Collections.emptyList())));
    interceptRequests.add(
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Collections.singletonList("java.lang.Integer"))));
    interceptRequests.add(
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));

    interceptRequests.forEach(
        interceptRequest -> {
          final Charset charset = Charset.defaultCharset();
          // serialize
          final byte[] bytes = interceptRequest.toBytes(charset);
          // deserialize
          final InterceptRequest request2 = InterceptRequest.fromBytes(bytes, charset);
          // compare
          assertThat(request2, is(interceptRequest));
        });
  }

  @Test
  public void compareSerializedSizeToInterceptMessage() {
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Arrays.asList("java.lang.String", "java.lang.Integer"),
            "org.package.Callback",
            "callMe");

    req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableFieldOp("out", FieldOpType.GET));

    interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "out",
            Intercepts.FieldOpType.GET,
            "org.package.Callback",
            "callMe");
  }
}
