/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for PalDirectory connection behavior and timeout handling. */
public class PalDirectoryConnectionIT {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  /**
   * Test that PalDirectory with blocking=true fails fast when etcd is unreachable.
   *
   * <p>This test verifies that the preflight health check (EtcdHealthCheck) successfully prevents
   * jetcd's gRPC connection logic from hanging indefinitely when etcd is unreachable.
   */
  @Test(timeout = 10000) // 10 second timeout as safety net
  public void constructor_blockingWithUnreachableEtcd_failsFast() {
    long startTime = System.currentTimeMillis();
    PalDirectory dir = null;

    try {
      // Try to connect to a port that will refuse connections (port 1 is reserved)
      // With blocking=true and preflight health check, this should fail within ~5 seconds
      dir = new PalDirectory("localhost:1", null, true);
      fail("Should have thrown RuntimeException for unreachable etcd");
    } catch (RuntimeException e) {
      long duration = System.currentTimeMillis() - startTime;
      logger.info("Connection attempt failed after {} ms: {}", duration, e.getMessage());

      // Verify it's a connection failure
      assertTrue(
          "Expected connection failure message",
          e.getMessage().contains("Failed to connect to etcd"));

      // Verify it failed reasonably fast (within 8 seconds)
      // The preflight check should fail within ~5 seconds, plus some buffer
      assertTrue(
          "Expected failure within 8 seconds, but took " + duration + " ms", duration < 8000);

      logger.info("✓ Preflight health check prevented hang - failed in {} ms", duration);
    } finally {
      if (dir != null) {
        dir.close();
      }
    }
  }

  /**
   * Test that PalDirectory with blocking=false does not validate connection immediately.
   *
   * <p>This test verifies that non-blocking mode allows construction to succeed even when etcd is
   * unreachable. The connection failure will only occur when operations are attempted.
   */
  @Test
  public void constructor_nonBlockingWithUnreachableEtcd_succeeds() {
    // With blocking=false, constructor should succeed even if etcd is unreachable
    PalDirectory dir = new PalDirectory("localhost:1", null, false);
    assertNotNull(dir);

    // The connection failure will only occur when we try to use it
    // (not testing that here as it would hang - that's the problem we're documenting)
    dir.close();
  }
}
