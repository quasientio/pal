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
