/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.messages.colfer.RaisedThrowable;
import org.junit.Test;

/**
 * Unit tests for {@link ExceptionSerdes}.
 *
 * <p>Tests serialization and deserialization of exceptions, including SecurityException which is
 * used in intercept callback tests.
 */
public class ExceptionSerdesTest {

  /** Tests that SecurityException can be round-tripped through serialization/deserialization. */
  @Test
  public void testSecurityExceptionRoundTrip() {
    String message = "Access denied by test";
    SecurityException original = new SecurityException(message);

    // Serialize
    RaisedThrowable raised = ExceptionSerdes.serializeException(original);
    assertThat(
        "Serialized type should be SecurityException",
        raised.getThrowable().getType(),
        is("java.lang.SecurityException"));
    assertThat("Serialized message should match", raised.getThrowable().getMessage(), is(message));

    // Deserialize
    Throwable deserialized = ExceptionSerdes.deserializeException(raised);
    assertThat(
        "Deserialized exception should be SecurityException",
        deserialized.getClass().getName(),
        is("java.lang.SecurityException"));
    assertThat("Deserialized message should match", deserialized.getMessage(), is(message));
  }

  /** Tests that RuntimeException can be round-tripped through serialization/deserialization. */
  @Test
  public void testRuntimeExceptionRoundTrip() {
    String message = "Runtime error";
    RuntimeException original = new RuntimeException(message);

    // Serialize
    RaisedThrowable raised = ExceptionSerdes.serializeException(original);
    assertThat(
        "Serialized type should be RuntimeException",
        raised.getThrowable().getType(),
        is("java.lang.RuntimeException"));

    // Deserialize
    Throwable deserialized = ExceptionSerdes.deserializeException(raised);
    assertThat(
        "Deserialized exception should be RuntimeException",
        deserialized.getClass().getName(),
        is("java.lang.RuntimeException"));
    assertThat("Deserialized message should match", deserialized.getMessage(), is(message));
  }

  /** Tests that IllegalArgumentException can be round-tripped. */
  @Test
  public void testIllegalArgumentExceptionRoundTrip() {
    String message = "Invalid argument";
    IllegalArgumentException original = new IllegalArgumentException(message);

    // Serialize
    RaisedThrowable raised = ExceptionSerdes.serializeException(original);

    // Deserialize
    Throwable deserialized = ExceptionSerdes.deserializeException(raised);
    assertThat(
        "Deserialized exception should be IllegalArgumentException",
        deserialized.getClass().getName(),
        is("java.lang.IllegalArgumentException"));
    assertThat("Deserialized message should match", deserialized.getMessage(), is(message));
  }

  /** Tests exception with cause can be round-tripped. */
  @Test
  public void testExceptionWithCauseRoundTrip() {
    RuntimeException cause = new RuntimeException("Root cause");
    SecurityException original = new SecurityException("Wrapper exception", cause);

    // Serialize
    RaisedThrowable raised = ExceptionSerdes.serializeException(original);

    // Deserialize
    Throwable deserialized = ExceptionSerdes.deserializeException(raised);
    assertThat(
        "Deserialized exception should be SecurityException",
        deserialized.getClass().getName(),
        is("java.lang.SecurityException"));
    assertThat(
        "Cause should be RuntimeException",
        deserialized.getCause().getClass().getName(),
        is("java.lang.RuntimeException"));
    assertThat(
        "Cause message should match", deserialized.getCause().getMessage(), is("Root cause"));
  }
}
