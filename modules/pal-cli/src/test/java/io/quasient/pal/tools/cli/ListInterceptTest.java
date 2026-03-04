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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
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
}
