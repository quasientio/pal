/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.logging;

/**
 * Provides a custom {@code ConsoleAppender} that maintains the original package and class name
 * after Maven shading. This ensures that the {@code logback.xml} configuration can reference this
 * appender without requiring changes to the package name. By extending Logback's {@link
 * ch.qos.logback.core.ConsoleAppender}, this class allows relocation of dependencies without
 * affecting internal classes. See issue #168 for more details.
 *
 * @see ch.qos.logback.core.ConsoleAppender
 */
@SuppressWarnings({"rawtypes", "PMD.NoFullyQualifiedTypes"})
public final class PeerConsoleAppender extends ch.qos.logback.core.ConsoleAppender {}
