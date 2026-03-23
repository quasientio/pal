/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

/**
 * Enumerates the build tools supported by the {@code pal init} command.
 *
 * @since 1.0.0
 */
public enum BuildTool {
  /** Apache Maven build tool. */
  MAVEN,

  /** Gradle build tool (Groovy or Kotlin DSL). */
  GRADLE
}
