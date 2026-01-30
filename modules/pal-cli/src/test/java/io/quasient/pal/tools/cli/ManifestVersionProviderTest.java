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

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link ManifestVersionProvider}.
 *
 * <p>ManifestVersionProvider implements picocli's IVersionProvider interface to retrieve the
 * application version from the JAR manifest's Implementation-Version attribute.
 */
public class ManifestVersionProviderTest {

  /**
   * Tests that getVersion() returns a non-null String array with exactly one element.
   *
   * <p>The ManifestVersionProvider.getVersion() method should always return a String array
   * containing a single element (the version string), regardless of whether the manifest contains
   * an Implementation-Version or not.
   */
  @Test
  @Ignore("Awaiting implementation in #357")
  public void testGetVersion_returnsVersionArray() {
    // Given: A ManifestVersionProvider instance

    // When: getVersion() is called

    // Then: Returns a non-null String array with exactly one element
    //       The array should never be null
    //       The array length should be exactly 1

    // TODO(#357): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that getVersion() returns the Implementation-Version from the package manifest.
   *
   * <p>The first element of the returned array should match the Implementation-Version specified in
   * the package's manifest. Note: In test context (running from classes directory rather than JAR),
   * the Implementation-Version may be null since the manifest is not present.
   */
  @Test
  @Ignore("Awaiting implementation in #357")
  public void testGetVersion_returnsImplementationVersion() {
    // Given: A ManifestVersionProvider instance

    // When: getVersion() is called

    // Then: The first element should be the package's Implementation-Version
    //       In test context (classes directory), this may be null
    //       In production (JAR with MANIFEST.MF), this should be the version string

    // TODO(#357): Implement after implementation is provided
    fail("Not yet implemented");
  }
}
