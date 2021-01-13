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

package net.ittera.pal.cxn;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * This class provides an indirect way to inject PALDirectory to peer classes. Dependency injection
 * cannot be used (at least not in a simple/straightforward way), since peer can run without a
 * PALDirectory connection, but we need to inject PALDirectory into classes that may need it if such
 * connection exists.
 *
 * <p>This factory is then used as a singleton that gets injected instead of PALDirectory. Classes
 * will then call getConnection() to get the Optional<PALDirectory> reference.
 *
 * <p>When peer runs without a pal directory, the factory must be instantiated with the special
 * connection string PALDirectory.NO_URL.
 */
public class DirectoryConnectionFactory {

  private final String connectionString;
  private PALDirectory palDirectoryInstance;

  @Inject
  public DirectoryConnectionFactory(@Named("paldir_url") String connectionString) {
    this.connectionString = connectionString;
  }

  public Optional<PALDirectory> getConnection() {
    if (connectionString.equals(PALDirectory.NO_URL)) {
      return Optional.empty();
    }

    if (palDirectoryInstance == null) {
      palDirectoryInstance = new PALDirectory(connectionString);
    }

    return Optional.of(palDirectoryInstance);
  }
}
