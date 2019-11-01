package com.ittera.cometa.core.exec.java;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.common.BiMapObjectService;
import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptMatchingTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private final ObjectService objectService = new BiMapObjectService();

  @Test
  public void antPathMatcherTests() {

    AntPathMatcherArrays matcher =
        new AntPathMatcherArrays.Builder()
            .withPathSeparator('.')
            .withTrimTokens()
            .withIgnoreCase()
            .build();

    final String interceptQ = "java .io.PrintStream. println";

    // should match
    assertThat(matcher.isMatch("java.io.PrintStream.println", interceptQ), is(true));
    assertThat(matcher.isMatch("java.io.PrintStream.print*", interceptQ), is(true));
    assertThat(matcher.isMatch("java.io.PrintStream.print??", interceptQ), is(true));
    assertThat(matcher.isMatch("java.io.*Stream.println", interceptQ), is(true));
    assertThat(matcher.isMatch("java.io.PrintStream.*", interceptQ), is(true));
    assertThat(matcher.isMatch("java.io.*.*", interceptQ), is(true));
    assertThat(matcher.isMatch("java.**.println", interceptQ), is(true));
    assertThat(matcher.isMatch("**.println", interceptQ), is(true));

    // should NOT match
    assertThat(matcher.isMatch("java.io.PrintStream.?", interceptQ), is(false));
  }

  @Test
  public void matchesConstructorWithNoParameters() {

    // create InterceptRequest message
    Intercepts.InterceptRequest interceptRequest =
        msgBuilder.buildInterceptRequest(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    Wrappers.ExecMessage execMessage =
        msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.util.ArrayList");

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptRequest);
    assertThat(interceptRequestEntry.matches(execMessage), is(true));
  }

  @Test
  public void matchesVoidInstanceMethodWithNoParameters() {

    // create InterceptRequest message
    Intercepts.InterceptRequest interceptRequest =
        msgBuilder.buildInterceptRequest(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    Object target = System.out;
    ObjectRef targetObjRef = objectService.storeObject(target);
    Wrappers.ExecMessage execMessage =
        msgBuilder.buildInstanceMethod(
            UUID.randomUUID(),
            "java.io.PrintStream",
            "println",
            target,
            targetObjRef,
            new String[0],
            new Object[0],
            new ObjectRef[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptRequest);
    assertThat(interceptRequestEntry.matches(execMessage), is(true));
  }

  @Test
  public void matchesVoidClassMethodWithNoParameters() {

    // create InterceptRequest message
    Intercepts.InterceptRequest interceptRequest =
        msgBuilder.buildInterceptRequest(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.lang.System",
            "gc",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    Object target = System.out;
    ObjectRef targetObjRef = objectService.storeObject(target);
    Wrappers.ExecMessage execMessage =
        msgBuilder.buildClassMethod(
            UUID.randomUUID(),
            "java.lang.System",
            "gc",
            new String[0],
            this,
            null,
            new Object[0],
            new ObjectRef[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptRequest);
    assertThat(interceptRequestEntry.matches(execMessage), is(true));
  }
}
