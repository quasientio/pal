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
 * AspectJ weaving infrastructure that transforms bytecode to capture operations as messages.
 *
 * <p>This module is the core of PAL's "operations as messages" abstraction. When application code
 * is compiled with this aspect library, all method calls, constructor invocations, and field
 * accesses are intercepted and forwarded to the PAL runtime for logging, routing, and interception.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link FullQuantizeAspect} - Main aspect with pointcuts and advice for all operation types
 *   <li>{@code FullQuantizeSoftening.aj} - Companion aspect for exception softening
 * </ul>
 *
 * <p><b>Usage:</b> Add this module as an aspectLibrary in the aspectj-maven-plugin configuration:
 *
 * <pre>{@code
 * <aspectLibraries>
 *     <aspectLibrary>
 *         <groupId>io.quasient.pal</groupId>
 *         <artifactId>pal-weave</artifactId>
 *     </aspectLibrary>
 * </aspectLibraries>
 * }</pre>
 */
package io.quasient.pal.weave;
