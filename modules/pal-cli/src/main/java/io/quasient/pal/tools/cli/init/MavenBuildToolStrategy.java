/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Maven-specific implementation of {@link BuildToolStrategy}.
 *
 * <p>Handles generation and patching of Maven {@code pom.xml} files with PAL AspectJ weaving
 * configuration.
 *
 * @since 1.0.0
 */
class MavenBuildToolStrategy implements BuildToolStrategy {

  /** Maven build file name. */
  private static final String BUILD_FILE_NAME = "pom.xml";

  /** Maven settings file name. */
  private static final String SETTINGS_FILE_NAME = "settings.xml";

  /** {@inheritDoc} */
  @Override
  public void generate(InitConfig config, Path outputDir) throws IOException {
    throw new UnsupportedOperationException("Maven generation not yet implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void patch(InitConfig config, Path buildFile) throws IOException {
    throw new UnsupportedOperationException("Maven patching not yet implemented");
  }

  /** {@inheritDoc} */
  @Override
  public String getBuildFileName() {
    return BUILD_FILE_NAME;
  }

  /** {@inheritDoc} */
  @Override
  public String getSettingsFileName() {
    return SETTINGS_FILE_NAME;
  }
}
