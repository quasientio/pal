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
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for intercept listing functionality in the List command.
 *
 * <p>Tests the formatting of intercept targets for method calls and field operations, and validates
 * the -I flag behavior.
 */
public class ListInterceptTest {

  /**
   * Tests formatting of a method call with no parameters.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_methodCallNoParams() throws Exception {
    InterceptableMethodCall method = new InterceptableMethodCall("doWork", Collections.emptyList());
    String result = List.formatInterceptTarget(method);
    assertThat(result, is("doWork()"));
  }

  /**
   * Tests formatting of a method call with simple parameter types.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_methodCallWithParams() throws Exception {
    InterceptableMethodCall method =
        new InterceptableMethodCall("add", Arrays.asList("int", "int"));
    String result = List.formatInterceptTarget(method);
    assertThat(result, is("add(int, int)"));
  }

  /**
   * Tests formatting of a method call with fully qualified parameter types.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_methodCallWithFqnParams() throws Exception {
    InterceptableMethodCall method =
        new InterceptableMethodCall("process", Arrays.asList("java.lang.String", "java.util.List"));
    String result = List.formatInterceptTarget(method);
    assertThat(result, is("process(String, List)"));
  }

  /**
   * Tests formatting of a field GET operation.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_fieldOpGet() throws Exception {
    InterceptableFieldOp fieldOp = new InterceptableFieldOp("counter", FieldOpType.GET);
    String result = List.formatInterceptTarget(fieldOp);
    assertThat(result, is("counter [GET]"));
  }

  /**
   * Tests formatting of a field PUT operation.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_fieldOpPut() throws Exception {
    InterceptableFieldOp fieldOp = new InterceptableFieldOp("value", FieldOpType.PUT);
    String result = List.formatInterceptTarget(fieldOp);
    assertThat(result, is("value [PUT]"));
  }

  /**
   * Tests that -I flag is mutually exclusive with -L.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void validateInput_interceptsAndLogs_throwsError() throws Exception {
    List listInstance = new List();
    Field listLogsField = List.class.getDeclaredField("listLogs");
    listLogsField.setAccessible(true);
    listLogsField.setBoolean(listInstance, true);

    Field listInterceptsField = List.class.getDeclaredField("listIntercepts");
    listInterceptsField.setAccessible(true);
    listInterceptsField.setBoolean(listInstance, true);

    try {
      listInstance.validateInput();
      fail("Expected RuntimeException for -L and -I together");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(), containsString("-L"));
      assertThat(e.getMessage(), containsString("-I"));
    }
  }

  /**
   * Tests that -I flag is mutually exclusive with -P.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void validateInput_interceptsAndPeers_throwsError() throws Exception {
    List listInstance = new List();
    Field listPeersField = List.class.getDeclaredField("listPeers");
    listPeersField.setAccessible(true);
    listPeersField.setBoolean(listInstance, true);

    Field listInterceptsField = List.class.getDeclaredField("listIntercepts");
    listInterceptsField.setAccessible(true);
    listInterceptsField.setBoolean(listInstance, true);

    try {
      listInstance.validateInput();
      fail("Expected RuntimeException for -P and -I together");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(), containsString("-P"));
      assertThat(e.getMessage(), containsString("-I"));
    }
  }

  /**
   * Tests that -I flag alone passes validation.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void validateInput_interceptsOnly_passes() throws Exception {
    List listInstance = new List();
    Field listInterceptsField = List.class.getDeclaredField("listIntercepts");
    listInterceptsField.setAccessible(true);
    listInterceptsField.setBoolean(listInstance, true);

    listInstance.validateInput();
  }

  /**
   * Tests formatting of a method call with a single parameter.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void formatInterceptTarget_methodCallSingleParam() throws Exception {
    InterceptableMethodCall method =
        new InterceptableMethodCall("setName", Collections.singletonList("java.lang.String"));
    String result = List.formatInterceptTarget(method);
    assertThat(result, is("setName(String)"));
  }

  /**
   * Tests that all three flags together is rejected.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void validateInput_allThreeFlags_throwsError() throws Exception {
    List listInstance = new List();
    Field listLogsField = List.class.getDeclaredField("listLogs");
    listLogsField.setAccessible(true);
    listLogsField.setBoolean(listInstance, true);

    Field listPeersField = List.class.getDeclaredField("listPeers");
    listPeersField.setAccessible(true);
    listPeersField.setBoolean(listInstance, true);

    Field listInterceptsField = List.class.getDeclaredField("listIntercepts");
    listInterceptsField.setAccessible(true);
    listInterceptsField.setBoolean(listInstance, true);

    try {
      listInstance.validateInput();
      fail("Expected RuntimeException for all three flags");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(), not(is("")));
    }
  }

  /**
   * Tests that an intercept with a TTL displays the TTL value in long format.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void print_interceptWithTtl_showsTtlInLongFormat() throws Exception {
    // Given: InterceptRequest with ttlSeconds=300, long listing mode enabled
    List listInstance = new List();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);

    Field outField = List.class.getSuperclass().getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(listInstance, printStream);

    Field longListingField = List.class.getDeclaredField("longListing");
    longListingField.setAccessible(true);
    longListingField.setBoolean(listInstance, true);

    InterceptableMethodCall method =
        new InterceptableMethodCall("add", Arrays.asList("int", "int"));
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Calculator",
            "com.example.Handler",
            "onAdd",
            method,
            false,
            null,
            null,
            0,
            300);

    // When: print(intercept) called via reflection
    Method printMethod = List.class.getDeclaredMethod("print", InterceptRequest.class);
    printMethod.setAccessible(true);
    printMethod.invoke(listInstance, intercept);

    // Then: Output line contains "300s" in the TTL column position
    String output = baos.toString(UTF_8);
    assertThat(output, containsString("300s"));
  }

  /**
   * Tests that an intercept with zero TTL displays a dash in long format.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void print_interceptWithZeroTtl_showsDashInLongFormat() throws Exception {
    // Given: InterceptRequest with ttlSeconds=0 (no TTL), long listing mode enabled
    List listInstance = new List();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);

    Field outField = List.class.getSuperclass().getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(listInstance, printStream);

    Field longListingField = List.class.getDeclaredField("longListing");
    longListingField.setAccessible(true);
    longListingField.setBoolean(listInstance, true);

    InterceptableMethodCall method =
        new InterceptableMethodCall("add", Arrays.asList("int", "int"));
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Calculator",
            "com.example.Handler",
            "onAdd",
            method);

    // When: print(intercept) called via reflection
    Method printMethod = List.class.getDeclaredMethod("print", InterceptRequest.class);
    printMethod.setAccessible(true);
    printMethod.invoke(listInstance, intercept);

    // Then: Output line contains "-" for no-TTL in the TTL column
    String output = baos.toString(UTF_8);
    assertThat(output, containsString("-"));
    assertThat(output, not(containsString("0s")));
  }

  /**
   * Tests that short format output does not include TTL information.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void print_interceptShortFormat_noTtl() throws Exception {
    // Given: InterceptRequest with ttlSeconds=300, short listing mode (no -l flag)
    UUID interceptUuid = UUID.randomUUID();
    List listInstance = new List();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);

    Field outField = List.class.getSuperclass().getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(listInstance, printStream);

    InterceptableMethodCall method =
        new InterceptableMethodCall("add", Arrays.asList("int", "int"));
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            interceptUuid,
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Calculator",
            "com.example.Handler",
            "onAdd",
            method,
            false,
            null,
            null,
            0,
            300);

    // When: print(intercept) called via reflection (longListing defaults to false)
    Method printMethod = List.class.getDeclaredMethod("print", InterceptRequest.class);
    printMethod.setAccessible(true);
    printMethod.invoke(listInstance, intercept);

    // Then: Output only contains UUID (no TTL displayed)
    String output = baos.toString(UTF_8).trim();
    assertThat(output, is(interceptUuid.toString()));
  }
}
