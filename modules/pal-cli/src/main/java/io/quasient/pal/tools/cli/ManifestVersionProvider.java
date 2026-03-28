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
