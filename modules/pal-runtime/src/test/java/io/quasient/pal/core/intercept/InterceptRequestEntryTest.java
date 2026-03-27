/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntryTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final ObjectLookupStore objectLookupStore =
      ConcurrentHashMapObjectLookupStore.createAsyncManaged();

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
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            List.of(),
            "org.some.package.MyInterceptor",
            "callMe");

    // create Exec message
    ExecMessage execMessage =
        msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.util.ArrayList");
    final String classname = ExecMessageUtils.getClassname(execMessage);
    final String executableName = ExecMessageUtils.getExecutableName(execMessage);
    final List<String> paramTypesList = getParameterTypes(execMessage);
    final String[] parameterTypes =
        paramTypesList == null ? null : paramTypesList.toArray(new String[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(classname, executableName, parameterTypes), is(true));
  }

  @Test
  public void matchesVoidInstanceMethodWithNoParameters() {

    // create InterceptMessage message
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            List.of(),
            "org.some.package.MyInterceptor",
            "callMe");

    // create Exec message
    Object target = System.out;
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);
    ExecMessage execMessage =
        msgBuilder.buildInstanceMethod(
            UUID.randomUUID(),
            "java.io.PrintStream",
            "println",
            targetObjRef,
            new String[0],
            new Object[0],
            new ObjectRef[0]);
    final String classname = ExecMessageUtils.getClassname(execMessage);
    final String executableName = ExecMessageUtils.getExecutableName(execMessage);
    final List<String> paramTypesList = getParameterTypes(execMessage);
    final String[] parameterTypes =
        paramTypesList == null ? null : paramTypesList.toArray(new String[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(classname, executableName, parameterTypes), is(true));
  }

  @Test
  public void matchesVoidClassMethodWithNoParameters() {

    // create InterceptMessage message
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.lang.System",
            "gc",
            List.of(),
            "org.some.package.MyInterceptor",
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
    final String classname = ExecMessageUtils.getClassname(execMessage);
    final String executableName = ExecMessageUtils.getExecutableName(execMessage);
    final List<String> paramTypesList = getParameterTypes(execMessage);
    final String[] parameterTypes =
        paramTypesList == null ? null : paramTypesList.toArray(new String[0]);

    InterceptRequestEntry interceptRequestEntry = new InterceptRequestEntry(interceptMessage);
    assertThat(interceptRequestEntry.matches(classname, executableName, parameterTypes), is(true));
  }

  @Test
  public void matchesMethodWithNoParamsSpecified_matchesAnySignature() {
    // When an intercept is registered without specifying parameter types,
    // it should match any overload of the method (wildcard behavior).
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Calculator",
            "add",
            List.of(), // no param types specified
            "org.some.package.MyInterceptor",
            "callMe");

    InterceptRequestEntry entry = new InterceptRequestEntry(interceptMessage);

    // Should match a call with (int, int) params
    assertThat(
        entry.matches("com.example.Calculator", "add", new String[] {"int", "int"}), is(true));

    // Should match a call with (double, double) params
    assertThat(
        entry.matches("com.example.Calculator", "add", new String[] {"double", "double"}),
        is(true));

    // Should match a call with (java.lang.String) param
    assertThat(
        entry.matches("com.example.Calculator", "add", new String[] {"java.lang.String"}),
        is(true));

    // Should still match zero-arg overload
    assertThat(entry.matches("com.example.Calculator", "add", new String[0]), is(true));

    // Should NOT match a different method name
    assertThat(
        entry.matches("com.example.Calculator", "subtract", new String[] {"int", "int"}),
        is(false));
  }

  @Test
  public void matchesMethodWithExplicitParams_matchesOnlyThatSignature() {
    // When param types ARE specified, only that exact signature should match.
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Calculator",
            "add",
            List.of("int", "int"),
            "org.some.package.MyInterceptor",
            "callMe");

    InterceptRequestEntry entry = new InterceptRequestEntry(interceptMessage);

    // Should match exact param types
    assertThat(
        entry.matches("com.example.Calculator", "add", new String[] {"int", "int"}), is(true));

    // Should NOT match different param types
    assertThat(
        entry.matches("com.example.Calculator", "add", new String[] {"double", "double"}),
        is(false));

    // Should NOT match zero-arg
    assertThat(entry.matches("com.example.Calculator", "add", new String[0]), is(false));
  }

  @Test
  public void testGetPriorityDelegatesToMessage() {
    InterceptMessage interceptMessage =
        msgBuilder
            .buildInterceptMessage(
                UUID.randomUUID(),
                InterceptType.BEFORE,
                "com.example.Foo",
                "bar",
                List.of(),
                "com.example.Callback",
                "onIntercept")
            .withPriority(7);

    InterceptRequestEntry entry = new InterceptRequestEntry(interceptMessage);
    assertThat(entry.getPriority(), is(7));
  }

  @Test
  public void testGetPriorityDefaultsToZero() {
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Foo",
            "bar",
            List.of(),
            "com.example.Callback",
            "onIntercept");

    InterceptRequestEntry entry = new InterceptRequestEntry(interceptMessage);
    assertThat(entry.getPriority(), is(0));
  }

  @Test
  public void getCallbackTimeoutMs_delegatesToMessage() {
    InterceptMessage interceptMessage =
        msgBuilder
            .buildInterceptMessage(
                UUID.randomUUID(),
                InterceptType.BEFORE,
                "com.example.Foo",
                "bar",
                List.of(),
                "com.example.Callback",
                "onIntercept")
            .withCallbackTimeoutMs(5000L);

    InterceptRequestEntry entry = new InterceptRequestEntry(interceptMessage);
    assertThat(entry.getCallbackTimeoutMs(), is(5000L));
  }
}
