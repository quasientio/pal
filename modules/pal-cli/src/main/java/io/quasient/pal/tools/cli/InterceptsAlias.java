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

import picocli.CommandLine.Command;

/**
 * Shortcut command that lists intercepts directly from the root {@code pal} command.
 *
 * <p>Running {@code pal intercepts} is equivalent to {@code pal intercept ls}. This alias is
 * registered as a direct child of the root {@link Pal} command so that its {@code @ParentCommand
 * PalCommand} resolves to {@link Pal} directly, enabling correct propagation of the directory
 * connection string.
 *
 * @see InterceptList
 * @see InterceptCommand
 */
@Command(name = "intercepts", description = "List intercepts (shorthand for 'intercept ls')")
public class InterceptsAlias extends InterceptList {

  /** Constructs a new {@code InterceptsAlias} instance. */
  InterceptsAlias() {}
}
