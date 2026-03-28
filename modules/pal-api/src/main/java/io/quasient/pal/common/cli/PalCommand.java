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
package io.quasient.pal.common.cli;

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
