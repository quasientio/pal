/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.tools.cli;

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
