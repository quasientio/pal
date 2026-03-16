/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for the {@code pal intercept rm} command.
 *
 * <p>Tests removal of intercept bundles by file, by bundle name, and by peer name. Each test first
 * applies a bundle, then removes it using a different strategy and verifies cleanup.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class InterceptBundleRemoveIT extends AbstractCliIT {

  /**
   * Tests that {@code pal intercept rm -f <file>} removes all intercepts defined in the file.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1245")
  public void testRemove_byFile_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    // When: Running `pal intercept rm -d <url> -f <file>`
    // Then: Exit code is 0, PalDirectory.listInterceptsForPeer() returns empty,
    //       PalDirectory.getBundleMetadata(bundleName) returns null

    // TODO(#1245): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal intercept rm --bundle <name>} removes all intercepts in the bundle.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1245")
  public void testRemove_byBundle_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    // When: Running `pal intercept rm -d <url> --bundle <name>`
    // Then: Exit code is 0, PalDirectory.listInterceptsForPeer() returns empty,
    //       PalDirectory.getBundleMetadata(bundleName) returns null

    // TODO(#1245): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal intercept rm --peer <name>} removes all intercepts for a peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1245")
  public void testRemove_byPeer_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd for the peer)
    // When: Running `pal intercept rm -d <url> --peer <name>`
    // Then: Exit code is 0, all intercepts for that peer are gone from etcd

    // TODO(#1245): Implement test logic
    fail("Not yet implemented");
  }
}
