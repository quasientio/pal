/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for directory-related exception classes.
 *
 * <p>Tests the constructors and behavior of {@link NoPeerInfoNodeException} and {@link
 * EtcdUnavailableException}.
 */
public class DirectoryExceptionsTest {

  // ==================== NoPeerInfoNodeException tests ====================

  /** Tests NoPeerInfoNodeException constructor with message. */
  @Test
  public void testNoPeerInfoNodeException_withMessage() {
    String message = "Peer info not found";
    NoPeerInfoNodeException ex = new NoPeerInfoNodeException(message);

    assertThat("Message should be set", ex.getMessage(), is(message));
    assertThat("Cause should be null", ex.getCause(), nullValue());
  }

  /** Tests that NoPeerInfoNodeException hierarchy is correct via reflection. */
  @Test
  public void testNoPeerInfoNodeException_hierarchy() {
    // Verify NoPeerInfoNodeException extends Exception but not RuntimeException
    assertTrue(
        "Should extend Exception", Exception.class.isAssignableFrom(NoPeerInfoNodeException.class));
    assertFalse(
        "Should not extend RuntimeException",
        RuntimeException.class.isAssignableFrom(NoPeerInfoNodeException.class));
  }

  // ==================== EtcdUnavailableException tests ====================

  /** Tests EtcdUnavailableException constructor with message only. */
  @Test
  public void testEtcdUnavailableException_withMessage() {
    String message = "etcd is down";
    EtcdUnavailableException ex = new EtcdUnavailableException(message);

    assertThat("Message should be set", ex.getMessage(), is(message));
    assertThat("Cause should be null", ex.getCause(), nullValue());
  }

  /** Tests EtcdUnavailableException constructor with message and cause. */
  @Test
  public void testEtcdUnavailableException_withMessageAndCause() {
    String message = "etcd connection failed";
    Throwable cause = new RuntimeException("connection refused");
    EtcdUnavailableException ex = new EtcdUnavailableException(message, cause);

    assertThat("Message should be set", ex.getMessage(), is(message));
    assertThat("Cause should be set", ex.getCause(), sameInstance(cause));
  }

  /** Tests that EtcdUnavailableException hierarchy is correct via reflection. */
  @Test
  public void testEtcdUnavailableException_hierarchy() {
    // Verify EtcdUnavailableException extends RuntimeException
    assertTrue(
        "Should extend RuntimeException",
        RuntimeException.class.isAssignableFrom(EtcdUnavailableException.class));
  }

  /** Tests that EtcdUnavailableException can be thrown and caught. */
  @Test(expected = EtcdUnavailableException.class)
  public void testEtcdUnavailableException_canBeThrown() {
    throw new EtcdUnavailableException("test throw");
  }

  /** Tests exception message with detailed information. */
  @Test
  public void testEtcdUnavailableException_detailedMessage() {
    String endpoint = "http://localhost:2379";
    int timeoutMs = 5000;
    String message =
        String.format("etcd endpoint %s is not reachable within %d ms", endpoint, timeoutMs);

    EtcdUnavailableException ex = new EtcdUnavailableException(message);

    assertTrue("Message should contain endpoint", ex.getMessage().contains(endpoint));
    assertTrue(
        "Message should contain timeout", ex.getMessage().contains(String.valueOf(timeoutMs)));
  }
}
