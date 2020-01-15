package net.ittera.pal.core.exec.java;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.Arrays;
import java.util.UUID;
import net.ittera.pal.common.ConcurrentHashMapObjectStore;
import net.ittera.pal.common.ObjectStore;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Intercepts;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntryTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private final ObjectStore objectStore = new ConcurrentHashMapObjectStore();

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

    // create InterceptMessage message
    Intercepts.InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    ExecMessage execMessage =
        msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.util.ArrayList");

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(msgBuilder.buildInterceptKey(execMessage)), is(true));
  }

  @Test
  public void matchesVoidInstanceMethodWithNoParameters() {

    // create InterceptMessage message
    Intercepts.InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    Object target = System.out;
    ObjectRef targetObjRef = objectStore.storeObject(target);
    ExecMessage execMessage =
        msgBuilder.buildInstanceMethod(
            UUID.randomUUID(),
            "java.io.PrintStream",
            "println",
            target,
            targetObjRef,
            new String[0],
            new Object[0],
            new ObjectRef[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(msgBuilder.buildInterceptKey(execMessage)), is(true));
  }

  @Test
  public void matchesVoidClassMethodWithNoParameters() {

    // create InterceptMessage message
    Intercepts.InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            Intercepts.InterceptType.BEFORE,
            "java.lang.System",
            "gc",
            Arrays.asList(new String[0]),
            "org.somepackage.MyInterceptor",
            "callMe");

    // create Exec message
    ExecMessage execMessage =
        msgBuilder.buildClassMethod(
            UUID.randomUUID(),
            "java.lang.System",
            "gc",
            new String[0],
            this,
            null,
            new Object[0],
            new ObjectRef[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(msgBuilder.buildInterceptKey(execMessage)), is(true));
  }
}
