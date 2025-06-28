/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides version information extracted from the application's manifest.
 *
 * <p>This implementation of {@link IVersionProvider} retrieves the implementation version specified
 * in the package's manifest, enabling version reporting in command-line interfaces.
 */
public class ManifestVersionProvider implements IVersionProvider {

  /**
   * {@inheritDoc}
   *
   * <p>Returns the implementation version defined in the package's manifest.
   *
   * @return an array containing the implementation version, or {@code null} if not available
   */
  @Override
  public String[] getVersion() {
    Package p = getClass().getPackage();
    return new String[] {p.getImplementationVersion()};
  }
}
