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

package net.ittera.pal.core.rpc.exec.java;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.serdes.colfer.ExecMessageUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptRequestEntryTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final ObjectLookupStore objectLookupStore = new ConcurrentHashMapObjectLookupStore();

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
