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
/**
 * PAL command-line interface implementation using picocli.
 *
 * <p>{@link Pal} is the main entry point. Available subcommands:
 *
 * <ul>
 *   <li>{@code run} - Start a PAL peer (delegates to pal-runtime)
 *   <li>{@code print} - Print messages from a log ({@link MessageStreamPrinter})
 *   <li>{@code peer call} - Invoke a method on a remote peer ({@link PeerCall})
 *   <li>{@code log call} - Send method calls via a log ({@link LogCall})
 *   <li>{@code ls} - List peers and logs ({@link List})
 *   <li>{@code peer rm} - Remove peers ({@link PeerRemove})
 *   <li>{@code log rm} - Remove logs ({@link LogRemove})
 * </ul>
 */
package io.quasient.pal.tools.cli;
