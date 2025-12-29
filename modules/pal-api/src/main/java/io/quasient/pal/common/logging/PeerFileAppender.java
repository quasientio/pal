/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.logging;

/**
 * Custom FileAppender that preserves the fully qualified class name in logback.xml after
 * mvn-shading. This allows the relocation of dependencies without requiring changes to the logging
 * configuration. By extending Logback's {@link ch.qos.logback.core.FileAppender}, it ensures
 * consistent logging behavior while maintaining the original package structure for configuration
 * purposes. See issue #168 for more details.
 *
 * @see ch.qos.logback.core.FileAppender
 */
@SuppressWarnings({"rawtypes", "PMD.NoFullyQualifiedTypes"})
public final class PeerFileAppender extends ch.qos.logback.core.FileAppender {}
