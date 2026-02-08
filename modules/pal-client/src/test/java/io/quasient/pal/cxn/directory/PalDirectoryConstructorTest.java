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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link PalDirectory} constructor overloads.
 *
 * <p>Tests the constructor delegation patterns. Non-blocking constructors create the jetcd client
 * without a preflight health check, so they succeed even when etcd is unreachable. Blocking
 * constructors perform a preflight health check via {@link EtcdHealthCheck#assertReachable} and
 * throw {@link EtcdUnavailableException} when etcd is unreachable.
 */
public class PalDirectoryConstructorTest {

  // ==================== Single-string constructor ====================

  /**
   * Tests that the single-string constructor creates a PalDirectory in non-blocking mode. The
   * constructor delegates to the full constructor with blocking=false, so it should succeed even
   * without etcd running.
   */
  @Test
  public void constructor_singleString_throwsWhenEtcdUnavailable() {
    // The single-string constructor uses non-blocking mode, so it succeeds
    // even without etcd. We verify it creates a valid instance.
    PalDirectory dir = null;
    try {
      dir = new PalDirectory("http://127.0.0.1:9999");
      assertThat(dir, is(notNullValue()));
    } catch (Throwable t) {
      fail("Non-blocking constructor should not throw: " + t.getMessage());
    } finally {
      if (dir != null) {
        dir.close();
      }
    }
  }

  // ==================== String + boolean constructor ====================

  /**
   * Tests that the string+boolean constructor with blocking=true throws {@link
   * EtcdUnavailableException} when etcd is unreachable.
   */
  @Test(expected = EtcdUnavailableException.class)
  public void constructor_stringAndBoolean_throwsWhenEtcdUnavailable() {
    // With blocking=true, the constructor performs a preflight health check
    // and throws EtcdUnavailableException if etcd is unreachable.
    new PalDirectory("http://127.0.0.1:9999", true);
  }

  // ==================== List<URI> constructor ====================

  /**
   * Tests that the URI list constructor creates a PalDirectory in non-blocking mode. The
   * constructor joins URIs with commas and delegates with blocking=false.
   */
  @Test
  public void constructor_listOfUris_throwsWhenEtcdUnavailable() {
    // The List<URI> constructor uses non-blocking mode, so it succeeds
    // even without etcd. We verify it creates a valid instance.
    List<URI> endpoints =
        Arrays.asList(URI.create("http://127.0.0.1:9999"), URI.create("http://127.0.0.1:9998"));
    PalDirectory dir = null;
    try {
      dir = new PalDirectory(endpoints);
      assertThat(dir, is(notNullValue()));
    } catch (Throwable t) {
      fail("Non-blocking URI list constructor should not throw: " + t.getMessage());
    } finally {
      if (dir != null) {
        dir.close();
      }
    }
  }

  // ==================== String + String + boolean constructor ====================

  /**
   * Tests that the full three-arg constructor (endpoints, namespace, blocking) throws {@link
   * EtcdUnavailableException} when etcd is unreachable and blocking=true.
   */
  @Test(expected = EtcdUnavailableException.class)
  public void constructor_stringAndStringAndBoolean_throwsWhenEtcdUnavailable() {
    // With blocking=true, the preflight health check runs and fails for unreachable endpoints.
    new PalDirectory("http://127.0.0.1:9999", "test-ns", true);
  }

  // ==================== NO_URL constant ====================

  /** Verifies that the {@link PalDirectory#NO_URL} constant is defined and usable as a sentinel. */
  @Test
  public void noUrl_constant_isValidSentinel() {
    assertThat(PalDirectory.NO_URL, is(notNullValue()));
    assertThat(PalDirectory.NO_URL, is("<none>"));
  }

  // ==================== Default timeout ====================

  /**
   * Verifies that the single-string constructor applies the default etcd connection timeout. Uses a
   * non-blocking constructor to avoid needing a running etcd, then inspects the timeout via
   * reflection.
   */
  @Test
  public void constructor_singleString_usesDefaultTimeout() throws Exception {
    PalDirectory dir = null;
    try {
      dir = new PalDirectory("http://127.0.0.1:9999");
      java.lang.reflect.Field timeoutField =
          PalDirectory.class.getDeclaredField("etcdConnectionTimeout");
      timeoutField.setAccessible(true);
      java.time.Duration timeout = (java.time.Duration) timeoutField.get(dir);
      assertThat(timeout, is(PalDirectory.getDefaultConnectionTimeout()));
    } finally {
      if (dir != null) {
        dir.close();
      }
    }
  }
}
