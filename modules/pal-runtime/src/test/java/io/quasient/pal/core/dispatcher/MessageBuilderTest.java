/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Test specifications for MessageBuilder's declared exceptions handling.
 *
 * <p>These tests verify that MessageBuilder can optionally include declared exception metadata in
 * ExecMessage instances when building method invocations.
 */
public class MessageBuilderTest {

  private MessageBuilder messageBuilder;
  private UUID peerUuid;
  private ObjectRef targetObjRef;
  private ObjectRef senderObjRef;

  /** Test fixture class with methods that declare various exceptions. */
  public static class TestClass {

    /**
     * Method with multiple declared exceptions.
     *
     * @throws IOException if I/O error occurs
     * @throws SQLException if SQL error occurs
     */
    public void methodWithExceptions() throws IOException, SQLException {
      // Test fixture method
    }

    /**
     * Method with single declared exception.
     *
     * @throws IOException if I/O error occurs
     */
    public void methodWithException() throws IOException {
      // Test fixture method
    }

    /** Method with no declared exceptions. */
    public void methodWithoutExceptions() {
      // Test fixture method
    }

    /**
     * Static method with multiple declared exceptions.
     *
     * @throws IOException if I/O error occurs
     * @throws SQLException if SQL error occurs
     */
    public static void staticMethodWithExceptions() throws IOException, SQLException {
      // Test fixture method
    }

    /** Static method with no declared exceptions. */
    public static void staticMethodWithoutExceptions() {
      // Test fixture method
    }
  }

  /** Sets up test fixtures before each test. */
  @Before
  public void setUp() {
    messageBuilder = new MessageBuilder();
    peerUuid = UUID.randomUUID();
    targetObjRef = ObjectRef.from(1);
    senderObjRef = ObjectRef.from(2);
  }

  /**
   * Test specification: shouldIncludeDeclaredExceptionsWhenRequested
   *
   * <p>Acceptance Criterion: [TEST:MessageBuilderTest.shouldIncludeDeclaredExceptionsWhenRequested]
   * Includes exceptions when flag true
   *
   * <p>Given: Method signature with throws IOException, SQLException When: buildInstanceMethod()
   * called with includeDeclaredExceptions=true Then: ExecMessage.declaredExceptions contains both
   * class names
   */
  @Test
  public void shouldIncludeDeclaredExceptionsWhenRequested() {
    // Given: A method signature with declared exceptions (throws IOException, SQLException)
    String className = TestClass.class.getName();
    String methodName = "methodWithExceptions";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildInstanceMethod() is called with includeDeclaredExceptions=true
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            className,
            methodName,
            targetObjRef,
            parameterTypes,
            args,
            null /* argObjRefs */,
            true /* includeDeclaredExceptions */);

    // Then: ExecMessage.declaredExceptions should contain both exception class names
    assertThat(execMessage, notNullValue());
    assertThat(execMessage.getDeclaredExceptions(), notNullValue());
    assertThat(
        execMessage.getDeclaredExceptions(),
        arrayContaining("java.io.IOException", "java.sql.SQLException"));
  }

  /**
   * Test specification: shouldExcludeDeclaredExceptionsWhenNotRequested
   *
   * <p>Acceptance Criterion:
   * [TEST:MessageBuilderTest.shouldExcludeDeclaredExceptionsWhenNotRequested] Excludes exceptions
   * when flag false
   *
   * <p>Given: Method signature with throws IOException When: buildInstanceMethod() called with
   * includeDeclaredExceptions=false Then: ExecMessage.declaredExceptions is null
   */
  @Test
  public void shouldExcludeDeclaredExceptionsWhenNotRequested() {
    // Given: A method signature with declared exceptions (throws IOException)
    String className = TestClass.class.getName();
    String methodName = "methodWithException";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildInstanceMethod() is called with includeDeclaredExceptions=false
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            className,
            methodName,
            targetObjRef,
            parameterTypes,
            args,
            null /* argObjRefs */,
            false /* includeDeclaredExceptions */);

    // Then: ExecMessage.declaredExceptions should be null (not populated)
    assertThat(execMessage, notNullValue());
    assertThat(execMessage.getDeclaredExceptions(), nullValue());
  }

  /**
   * Test specification: shouldHandleMethodWithNoDeclaredException
   *
   * <p>Acceptance Criterion: [TEST:MessageBuilderTest.shouldHandleMethodWithNoDeclaredException]
   * Handles no-throws methods
   *
   * <p>Given: Method signature with no throws clause When: buildInstanceMethod() called with
   * includeDeclaredExceptions=true Then: ExecMessage.declaredExceptions is empty array
   */
  @Test
  public void shouldHandleMethodWithNoDeclaredException() {
    // Given: A method signature with no declared exceptions (no throws clause)
    String className = TestClass.class.getName();
    String methodName = "methodWithoutExceptions";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildInstanceMethod() is called with includeDeclaredExceptions=true
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            className,
            methodName,
            targetObjRef,
            parameterTypes,
            args,
            null /* argObjRefs */,
            true /* includeDeclaredExceptions */);

    // Then: ExecMessage.declaredExceptions should be an empty array
    assertThat(execMessage, notNullValue());
    String[] declaredExceptions = execMessage.getDeclaredExceptions();
    assertThat(declaredExceptions, notNullValue());
    assertThat(declaredExceptions.length, is(0));
  }

  /**
   * Additional test: verify buildClassMethod also supports includeDeclaredExceptions parameter.
   *
   * <p>Given: Static method signature with throws IOException, SQLException When:
   * buildClassMethod() called with includeDeclaredExceptions=true Then:
   * ExecMessage.declaredExceptions contains both class names
   */
  @Test
  public void shouldIncludeDeclaredExceptionsForClassMethod() {
    // Given: A static method signature with declared exceptions
    String className = TestClass.class.getName();
    String methodName = "staticMethodWithExceptions";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildClassMethod() is called with includeDeclaredExceptions=true
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            className,
            methodName,
            parameterTypes,
            null,
            senderObjRef,
            args,
            null /* argObjRefs */,
            true /* includeDeclaredExceptions */);

    // Then: ExecMessage.declaredExceptions should contain both exception class names
    assertThat(execMessage, notNullValue());
    assertThat(execMessage.getDeclaredExceptions(), notNullValue());
    assertThat(
        execMessage.getDeclaredExceptions(),
        arrayContaining("java.io.IOException", "java.sql.SQLException"));
  }

  /**
   * Additional test: verify buildClassMethod excludes exceptions when flag is false.
   *
   * <p>Given: Static method signature with throws IOException, SQLException When:
   * buildClassMethod() called with includeDeclaredExceptions=false Then:
   * ExecMessage.declaredExceptions is null
   */
  @Test
  public void shouldExcludeDeclaredExceptionsForClassMethodWhenNotRequested() {
    // Given: A static method signature with declared exceptions
    String className = TestClass.class.getName();
    String methodName = "staticMethodWithExceptions";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildClassMethod() is called with includeDeclaredExceptions=false
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            className,
            methodName,
            parameterTypes,
            null,
            senderObjRef,
            args,
            null /* argObjRefs */,
            false /* includeDeclaredExceptions */);

    // Then: ExecMessage.declaredExceptions should be null
    assertThat(execMessage, notNullValue());
    assertThat(execMessage.getDeclaredExceptions(), nullValue());
  }

  /**
   * Additional test: verify backward compatibility - methods without includeDeclaredExceptions
   * parameter should have null declaredExceptions.
   *
   * <p>Given: Method signature with throws IOException When: buildInstanceMethod() called without
   * includeDeclaredExceptions parameter Then: ExecMessage.declaredExceptions is null (backward
   * compatible)
   */
  @Test
  public void shouldDefaultToExcludingDeclaredExceptionsForBackwardCompatibility() {
    // Given: A method signature with declared exceptions
    String className = TestClass.class.getName();
    String methodName = "methodWithException";
    String[] parameterTypes = new String[0];
    Object[] args = new Object[0];

    // When: buildInstanceMethod() is called without includeDeclaredExceptions parameter
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid, className, methodName, targetObjRef, parameterTypes, args);

    // Then: ExecMessage.declaredExceptions should be null (backward compatible default)
    assertThat(execMessage, notNullValue());
    assertThat(execMessage.getDeclaredExceptions(), nullValue());
  }
}
