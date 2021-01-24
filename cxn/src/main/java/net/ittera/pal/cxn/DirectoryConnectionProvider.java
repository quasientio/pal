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
import javax.inject.Provider;

/**
 * This class implements a null-safe Provider for PALDirectory.
 *
 * <p>It is used as a singleton that gets injected instead of PALDirectory. Methods that need a
 * connection need to call get() which returns the Optional<PALDirectory> reference.
 *
 * <p>When peer runs without a pal directory, the Provider is initialized with the constant
 * PALDirectory.NO_URL.
 */
public class DirectoryConnectionProvider implements Provider<Optional<PALDirectory>> {

  // PALDirectory constructor parameters
  private final String connectionString;
  private final String namespace;
  private final boolean withCaching;

  private PALDirectory palDirectoryInstance;

  @Inject
  public DirectoryConnectionProvider(@Named("paldir_url") String connectionString) {
    this(connectionString, null, true);
  }

  public DirectoryConnectionProvider(
      String connectionString, String namespace, boolean withCaching) {
    this.connectionString = connectionString;
    this.namespace = namespace;
    this.withCaching = withCaching;
  }

  @Override
  public Optional<PALDirectory> get() {
    if (connectionString.equals(PALDirectory.NO_URL)) {
      return Optional.empty();
    }

    if (palDirectoryInstance == null) {
      palDirectoryInstance = new PALDirectory(connectionString, namespace, withCaching);
    }

    return Optional.of(palDirectoryInstance);
  }

  public String getConnectionString() {
    return connectionString;
  }
}
