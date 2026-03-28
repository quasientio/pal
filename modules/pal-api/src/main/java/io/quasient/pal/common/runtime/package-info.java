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
 * Runtime execution context and message dispatching infrastructure.
 *
 * <p>This package provides the core abstractions for capturing AspectJ join points and dispatching
 * the resulting messages. The {@link Dispatcher} interface is the entry point for the "hot path" -
 * the code that runs for every quantized operation.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link Context} - Execution context extracted from AspectJ join points
 *   <li>{@link Dispatcher} - Interface for message dispatch implementations
 *   <li>{@link ExecPhase} - Lifecycle phases of an operation (DISPATCH, PROCEED, RETURN)
 * </ul>
 */
package io.quasient.pal.common.runtime;
