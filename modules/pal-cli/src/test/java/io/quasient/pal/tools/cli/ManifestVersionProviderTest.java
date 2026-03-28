/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
  public void testGetVersion_returnsVersionArray() {
    // Given: A ManifestVersionProvider instance
    ManifestVersionProvider provider = new ManifestVersionProvider();

    // When: getVersion() is called
    String[] version = provider.getVersion();

    // Then: Returns a non-null String array with exactly one element
    assertNotNull("getVersion() should not return null", version);
    assertEquals("getVersion() should return array with exactly one element", 1, version.length);
  }

  /**
   * Tests that getVersion() returns the Implementation-Version from the package manifest.
   *
   * <p>The first element of the returned array should match the Implementation-Version specified in
   * the package's manifest. Note: In test context (running from classes directory rather than JAR),
   * the Implementation-Version may be null since the manifest is not present.
   */
  @Test
  public void testGetVersion_returnsImplementationVersion() {
    // Given: A ManifestVersionProvider instance
    ManifestVersionProvider provider = new ManifestVersionProvider();
    Package pkg = provider.getClass().getPackage();
    String expectedVersion = pkg.getImplementationVersion();

    // When: getVersion() is called
    String[] version = provider.getVersion();

    // Then: The first element should be the package's Implementation-Version
    //       In test context (classes directory), this may be null
    //       In production (JAR with MANIFEST.MF), this should be the version string
    assertEquals(
        "getVersion()[0] should match package Implementation-Version", expectedVersion, version[0]);
  }
}
