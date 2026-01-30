/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

/**
 * Unit tests for {@link JsonRpcErrorData}.
 *
 * <p>Tests getters, setters, equals, hashCode, toString, and the Builder pattern.
 */
public class JsonRpcErrorDataTest {

  // ===== Default Constructor Tests =====

  /** Tests that default constructor creates object with null fields. */
  @Test
  public void defaultConstructor_createsEmptyObject() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();

    assertThat(errorData.getMessage(), nullValue());
    assertThat(errorData.getThrowableType(), nullValue());
    assertThat(errorData.getStackTrace(), nullValue());
    assertThat(errorData.getCause(), nullValue());
    assertThat(errorData.getRequestId(), nullValue());
    assertThat(errorData.getFrom(), nullValue());
  }

  // ===== Getter/Setter Tests =====

  /** Tests message getter and setter. */
  @Test
  public void setMessage_andGetMessage_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setMessage("Test error message");

    assertThat(errorData.getMessage(), is("Test error message"));
  }

  /** Tests throwable type getter and setter. */
  @Test
  public void setThrowableType_andGetThrowableType_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setThrowableType("java.lang.NullPointerException");

    assertThat(errorData.getThrowableType(), is("java.lang.NullPointerException"));
  }

  /** Tests stack trace getter and setter. */
  @Test
  public void setStackTrace_andGetStackTrace_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    String[] stackTrace = new String[] {"at Method1", "at Method2", "at Method3"};
    errorData.setStackTrace(stackTrace);

    assertThat(
        errorData.getStackTrace(), arrayContaining("at Method1", "at Method2", "at Method3"));
  }

  /** Tests cause getter and setter. */
  @Test
  public void setCause_andGetCause_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    JsonRpcErrorData cause = new JsonRpcErrorData();
    cause.setMessage("Root cause");
    errorData.setCause(cause);

    assertThat(errorData.getCause(), notNullValue());
    assertThat(errorData.getCause().getMessage(), is("Root cause"));
  }

  /** Tests request ID getter and setter. */
  @Test
  public void setRequestId_andGetRequestId_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setRequestId("req-12345");

    assertThat(errorData.getRequestId(), is("req-12345"));
  }

  /** Tests from (Executable) getter and setter. */
  @Test
  public void setFrom_andGetFrom_work() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    Executable executable = new Executable();
    executable.setClassName("com.example.Service");
    executable.setMethodName("process");
    errorData.setFrom(executable);

    assertThat(errorData.getFrom(), notNullValue());
    assertThat(errorData.getFrom().getClassName(), is("com.example.Service"));
  }

  /** Tests setting null cause. */
  @Test
  public void setCause_toNull_setsNull() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    JsonRpcErrorData cause = new JsonRpcErrorData();
    errorData.setCause(cause);
    errorData.setCause(null);

    assertThat(errorData.getCause(), nullValue());
  }

  // ===== equals() Tests =====

  /** Tests equals with same object. */
  @Test
  public void equals_sameObject_returnsTrue() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setMessage("error");

    assertThat(errorData.equals(errorData), is(true));
  }

  /** Tests equals with equal objects. */
  @Test
  public void equals_equalObjects_returnsTrue() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setMessage("error");
    error1.setThrowableType("java.lang.Exception");
    error1.setRequestId("req-1");

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setMessage("error");
    error2.setThrowableType("java.lang.Exception");
    error2.setRequestId("req-1");

    assertThat(error1, equalTo(error2));
  }

  /** Tests equals with different message. */
  @Test
  public void equals_differentMessage_returnsFalse() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setMessage("error1");

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setMessage("error2");

    assertThat(error1, not(equalTo(error2)));
  }

  /** Tests equals with different throwable type. */
  @Test
  public void equals_differentThrowableType_returnsFalse() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setThrowableType("java.lang.Exception");

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setThrowableType("java.lang.RuntimeException");

    assertThat(error1, not(equalTo(error2)));
  }

  /** Tests equals with different request ID. */
  @Test
  public void equals_differentRequestId_returnsFalse() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setRequestId("req-1");

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setRequestId("req-2");

    assertThat(error1, not(equalTo(error2)));
  }

  /** Tests equals with different stack trace. */
  @Test
  public void equals_differentStackTrace_returnsFalse() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setStackTrace(new String[] {"line1"});

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setStackTrace(new String[] {"line2"});

    assertThat(error1, not(equalTo(error2)));
  }

  /** Tests equals with different cause. */
  @Test
  public void equals_differentCause_returnsFalse() {
    JsonRpcErrorData cause1 = new JsonRpcErrorData();
    cause1.setMessage("cause1");
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setCause(cause1);

    JsonRpcErrorData cause2 = new JsonRpcErrorData();
    cause2.setMessage("cause2");
    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setCause(cause2);

    assertThat(error1, not(equalTo(error2)));
  }

  /** Tests equals with null. */
  @Test
  public void equals_withNull_returnsFalse() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();

    assertThat(errorData.equals(null), is(false));
  }

  /** Tests equals with different type. */
  @Test
  @SuppressWarnings("EqualsIncompatibleType")
  public void equals_differentType_returnsFalse() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();

    // Intentionally testing incompatible type comparison
    assertThat(errorData.equals("string"), is(false));
  }

  // ===== hashCode() Tests =====

  /** Tests hashCode consistency. */
  @Test
  public void hashCode_consistentForSameObject() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setMessage("error");
    errorData.setThrowableType("Exception");

    int hash1 = errorData.hashCode();
    int hash2 = errorData.hashCode();

    assertThat(hash1, is(hash2));
  }

  /** Tests hashCode equality for equal objects. */
  @Test
  public void hashCode_equalForEqualObjects() {
    JsonRpcErrorData error1 = new JsonRpcErrorData();
    error1.setMessage("error");
    error1.setThrowableType("Exception");

    JsonRpcErrorData error2 = new JsonRpcErrorData();
    error2.setMessage("error");
    error2.setThrowableType("Exception");

    assertThat(error1.hashCode(), is(error2.hashCode()));
  }

  // ===== toString() Tests =====

  /** Tests toString contains message. */
  @Test
  public void toString_containsMessage() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setMessage("Test error");

    assertThat(errorData.toString(), containsString("Test error"));
  }

  /** Tests toString contains throwable type. */
  @Test
  public void toString_containsThrowableType() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setThrowableType("java.lang.IllegalArgumentException");

    assertThat(errorData.toString(), containsString("IllegalArgumentException"));
  }

  /** Tests toString contains request ID. */
  @Test
  public void toString_containsRequestId() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();
    errorData.setRequestId("req-abc-123");

    assertThat(errorData.toString(), containsString("req-abc-123"));
  }

  /** Tests toString contains class name. */
  @Test
  public void toString_containsClassName() {
    JsonRpcErrorData errorData = new JsonRpcErrorData();

    assertThat(errorData.toString(), containsString("JsonRpcErrorData"));
  }

  // ===== Builder Tests =====

  /** Tests builder creates object. */
  @Test
  public void builder_createsObject() {
    JsonRpcErrorData errorData = JsonRpcErrorData.builder().build();

    assertThat(errorData, notNullValue());
  }

  /** Tests builder with message. */
  @Test
  public void builder_withMessage_setsMessage() {
    JsonRpcErrorData errorData =
        JsonRpcErrorData.builder().withMessage("Builder error message").build();

    assertThat(errorData.getMessage(), is("Builder error message"));
  }

  /** Tests builder with throwable type. */
  @Test
  public void builder_withThrowableType_setsThrowableType() {
    JsonRpcErrorData errorData =
        JsonRpcErrorData.builder().withThrowableType("java.io.IOException").build();

    assertThat(errorData.getThrowableType(), is("java.io.IOException"));
  }

  /** Tests builder with request ID. */
  @Test
  public void builder_withRequestId_setsRequestId() {
    JsonRpcErrorData errorData = JsonRpcErrorData.builder().withRequestId("req-xyz").build();

    assertThat(errorData.getRequestId(), is("req-xyz"));
  }

  /** Tests builder with stack trace. */
  @Test
  public void builder_withStackTrace_setsStackTrace() {
    String[] trace = new String[] {"at line 1", "at line 2"};
    JsonRpcErrorData errorData = JsonRpcErrorData.builder().withStackTrace(trace).build();

    assertThat(errorData.getStackTrace(), arrayContaining("at line 1", "at line 2"));
  }

  /** Tests builder with cause. */
  @Test
  public void builder_withCause_setsCause() {
    JsonRpcErrorData cause = new JsonRpcErrorData();
    cause.setMessage("Nested cause");

    JsonRpcErrorData errorData = JsonRpcErrorData.builder().withCause(cause).build();

    assertThat(errorData.getCause(), notNullValue());
    assertThat(errorData.getCause().getMessage(), is("Nested cause"));
  }

  /** Tests builder with from (Executable). */
  @Test
  public void builder_withFrom_setsFrom() {
    Executable exec = new Executable();
    exec.setMethodName("testMethod");

    JsonRpcErrorData errorData = JsonRpcErrorData.builder().withFrom(exec).build();

    assertThat(errorData.getFrom(), notNullValue());
    assertThat(errorData.getFrom().getMethodName(), is("testMethod"));
  }

  /** Tests builder chaining. */
  @Test
  public void builder_chaining_setsAllFields() {
    JsonRpcErrorData cause = new JsonRpcErrorData();
    Executable exec = new Executable();

    JsonRpcErrorData errorData =
        JsonRpcErrorData.builder()
            .withMessage("Chained error")
            .withThrowableType("java.lang.Error")
            .withRequestId("req-chain")
            .withStackTrace(new String[] {"trace"})
            .withCause(cause)
            .withFrom(exec)
            .build();

    assertThat(errorData.getMessage(), is("Chained error"));
    assertThat(errorData.getThrowableType(), is("java.lang.Error"));
    assertThat(errorData.getRequestId(), is("req-chain"));
    assertThat(errorData.getStackTrace(), arrayContaining("trace"));
    assertThat(errorData.getCause(), is(cause));
    assertThat(errorData.getFrom(), is(exec));
  }
}
