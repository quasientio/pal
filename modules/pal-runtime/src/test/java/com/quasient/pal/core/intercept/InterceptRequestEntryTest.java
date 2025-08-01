/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.serdes.colfer.ExecMessageUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntryTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final ObjectLookupStore objectLookupStore =
      ConcurrentHashMapObjectLookupStore.createWithScheduledCleaner();

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
}
