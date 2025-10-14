/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.quasient.pal.AbstractIntegrationTest;
import org.junit.Test;

/**
 * Integration test: when the specified main class cannot be found, `pal run` should behave like the
 * `java` command: print the error to stderr and exit with code 1.
 */
public class MainClassNotFoundIT extends AbstractIntegrationTest {

  @Test
  public void testMissingMainClass_printsToStderrAndExit1() throws Exception {
    String missingClass = "com.example.DoesNotExist";

    ProcessResult result = runPalCommand(missingClass);

    assertEquals("Expected exit code 1 when main class cannot be found", 1, result.exitCode());
    assertThat(
        "Expected the standard java launcher error on stderr",
        result.stderr(),
        containsString("Error: Could not find or load main class " + missingClass));
  }
}
