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
 * Gradle-specific implementation of {@link BuildToolStrategy}.
 *
 * <p>Handles generation and patching of Gradle build files ({@code build.gradle} / {@code
 * build.gradle.kts}) with PAL AspectJ weaving configuration. Delegates to {@link GradleGenerator}
 * for new project generation and {@link GradlePatcher} for patching existing projects.
 *
 * @since 1.0.0
 */
class GradleBuildToolStrategy implements BuildToolStrategy {

  /** Gradle build file name. */
  private static final String BUILD_FILE_NAME = "build.gradle";

  /** Gradle settings file name. */
  private static final String SETTINGS_FILE_NAME = "settings.gradle";

  /** {@inheritDoc} */
  @Override
  public void generate(InitConfig config, Path outputDir) throws IOException {
    new GradleGenerator(config).generate(outputDir);
  }

  /** {@inheritDoc} */
  @Override
  public void patch(InitConfig config, Path buildFile) throws IOException {
    new GradlePatcher().patch(config, buildFile);
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
