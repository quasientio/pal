/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
