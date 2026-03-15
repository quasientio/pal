/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link InterceptList}.
 *
 * <p>InterceptList is the intercept-specific list command extracted from {@code List} to follow the
 * entity-operation pattern ({@code pal intercept ls}). It handles listing intercept registrations
 * in short and long formats, with sorting and reversal options.
 */
public class InterceptListTest {

  // ==================== Helper methods ====================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object to set the field on
   * @param fieldName the name of the field
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Creates an InterceptList instance with a mock PalDirectory injected and output captured.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @return a configured InterceptList instance
   */
  private static InterceptList createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    InterceptList cmd = new InterceptList();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    return cmd;
  }

  /**
   * Creates an InterceptRequest with a creation time set.
   *
   * @param clazz the class pattern
   * @param methodName the method name
   * @param ctime the creation time
   * @return a configured InterceptRequest
   */
  private static InterceptRequest<InterceptableMethodCall> createIntercept(
      String clazz, String methodName, OffsetDateTime ctime) {
    InterceptableMethodCall method =
        new InterceptableMethodCall(methodName, Arrays.asList("int", "int"));
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            clazz,
            "com.example.Handler",
            "onCallback",
            method);
    intercept.setCtime(ctime.toInstant().toEpochMilli());
    return intercept;
  }

  // ==================== runCommand() Tests ====================

  /**
   * Tests that short format lists intercept summaries.
   *
   * <p>Verifies that when no {@code -l} flag is set, runCommand prints intercept UUIDs, one per
   * line.
   */
  @Test
  public void runCommand_listsIntercepts_shortFormat() throws Exception {
    // Given: PalDirectory with intercepts registered
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    InterceptRequest<?> intercept1 = createIntercept("com.example.Foo", "doWork", now);
    InterceptRequest<?> intercept2 = createIntercept("com.example.Bar", "process", now);
    Set<InterceptRequest<?>> intercepts = new HashSet<>();
    intercepts.add(intercept1);
    intercepts.add(intercept2);
    when(mockDir.listAllIntercepts()).thenReturn(intercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InterceptList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked (no -l flag)
    Method runCommand = InterceptList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: prints intercept UUIDs, one per line
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    assertThat(output, containsString(intercept1.getUuid().toString()));
    assertThat(output, containsString(intercept2.getUuid().toString()));
  }

  /**
   * Tests that long format prints detailed intercept information.
   *
   * <p>Verifies that when the {@code -l} flag is set, runCommand prints detailed intercept info
   * including class pattern, method pattern, intercept type, callback peer, and TTL.
   */
  @Test
  public void runCommand_listsIntercepts_longFormat() throws Exception {
    // Given: PalDirectory with intercepts
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    InterceptRequest<?> intercept = createIntercept("com.example.Calculator", "add", now);
    Set<InterceptRequest<?>> intercepts = new HashSet<>();
    intercepts.add(intercept);
    when(mockDir.listAllIntercepts()).thenReturn(intercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InterceptList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "longListing", true);

    // When: -l flag set, runCommand() invoked
    Method runCommand = InterceptList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: prints header and detailed intercept info
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    assertThat(output, containsString("total 1"));
    assertThat(output, containsString("UUID"));
    assertThat(output, containsString(intercept.getUuid().toString()));
    assertThat(output, containsString("BEFORE"));
    assertThat(output, containsString("Calculator"));
  }

  /**
   * Tests that intercepts are sorted by creation time with newest first.
   *
   * <p>Verifies that when the {@code -c} flag is set, intercepts are listed in descending order of
   * creation time (newest first).
   */
  @Test
  public void runCommand_sortByCtime() throws Exception {
    // Given: intercepts with different creation times
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime t1 = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t2 = OffsetDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t3 = OffsetDateTime.of(2025, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    InterceptRequest<?> oldest = createIntercept("com.a.Oldest", "oldest", t1);
    InterceptRequest<?> middle = createIntercept("com.b.Middle", "middle", t2);
    InterceptRequest<?> newest = createIntercept("com.c.Newest", "newest", t3);

    Set<InterceptRequest<?>> intercepts = new HashSet<>();
    intercepts.add(oldest);
    intercepts.add(middle);
    intercepts.add(newest);
    when(mockDir.listAllIntercepts()).thenReturn(intercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InterceptList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "sortByCTime", true);

    // When: -c flag set, runCommand() invoked
    Method runCommand = InterceptList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: intercepts listed newest first
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    int newestIdx = output.indexOf(newest.getUuid().toString());
    int middleIdx = output.indexOf(middle.getUuid().toString());
    int oldestIdx = output.indexOf(oldest.getUuid().toString());
    assertThat("newest before middle", newestIdx < middleIdx, is(true));
    assertThat("middle before oldest", middleIdx < oldestIdx, is(true));
  }

  /**
   * Tests that the reverse flag reverses the output order.
   *
   * <p>Verifies that when the {@code -r} flag is set, the order of listed intercepts is reversed
   * compared to the default sorted order.
   */
  @Test
  public void runCommand_reverseOrder() throws Exception {
    // Given: intercepts that sort by class name by default
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    InterceptRequest<?> alpha = createIntercept("com.example.Alpha", "doAlpha", now);
    InterceptRequest<?> zulu = createIntercept("com.example.Zulu", "doZulu", now);

    Set<InterceptRequest<?>> intercepts = new HashSet<>();
    intercepts.add(alpha);
    intercepts.add(zulu);
    when(mockDir.listAllIntercepts()).thenReturn(intercepts);

    // Default order (by class name): Alpha before Zulu
    ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
    InterceptList cmd1 = createWithMockDirAndOutput(mockDir, bout1);

    Method runCommand = InterceptList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    runCommand.invoke(cmd1);
    String defaultOutput = bout1.toString(UTF_8);
    int alphaDefault = defaultOutput.indexOf(alpha.getUuid().toString());
    int zuluDefault = defaultOutput.indexOf(zulu.getUuid().toString());
    assertThat("default: alpha before zulu", alphaDefault < zuluDefault, is(true));

    // Reversed order
    ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
    InterceptList cmd2 = createWithMockDirAndOutput(mockDir, bout2);
    setField(cmd2, "reverseOrder", true);
    runCommand.invoke(cmd2);
    String reversedOutput = bout2.toString(UTF_8);
    int alphaReversed = reversedOutput.indexOf(alpha.getUuid().toString());
    int zuluReversed = reversedOutput.indexOf(zulu.getUuid().toString());
    assertThat("reversed: zulu before alpha", zuluReversed < alphaReversed, is(true));
  }

  // ==================== Empty Directory Tests ====================

  /**
   * Tests that an empty directory produces no output.
   *
   * <p>Verifies that when the directory contains no intercepts, runCommand prints nothing and exits
   * with code 0.
   */
  @Test
  public void runCommand_noInterceptsFound_printsNothing() throws Exception {
    // Given: PalDirectory with no intercepts
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listAllIntercepts()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InterceptList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked
    Method runCommand = InterceptList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: no output, exit code 0
    assertThat(result, is(0));
    assertThat(bout.toString(UTF_8), is(""));
  }
}
