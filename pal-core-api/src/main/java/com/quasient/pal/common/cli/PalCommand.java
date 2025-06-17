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

package com.quasient.pal.common.cli;

/**
 * Defines a contract for PAL (picocli) command classes to enable subcommands to reference the
 * parent command without introducing cyclic dependencies.
 *
 * <p><strong>Purpose and Role:</strong> By implementing this interface, the parent Pal command
 * class allows subcommands to use the {@code @ParentCommand} annotation, facilitating hierarchical
 * command structures within the PAL CLI.
 *
 * @see PalCommand#getPalDirectoryConnectionString()
 */
public interface PalCommand {

  /**
   * Retrieves the connection string used to establish a connection to the PAL directory.
   *
   * @return a {@code String} representing the connection string for the PAL directory.
   */
  String getPalDirectoryConnectionString();
}
