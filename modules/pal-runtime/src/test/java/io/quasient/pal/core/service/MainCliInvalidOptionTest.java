/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import picocli.CommandLine;

public class MainCliInvalidOptionTest {

  @Test
  public void unknownOption_throwsParameterException() {
    CommandLine.ParameterException pe =
        assertThrows(
            CommandLine.ParameterException.class,
            () -> new CommandLine(new Main()).parseArgs("--unknown-option"));
    assertThat(pe.getMessage(), containsString("--unknown-option"));
  }
}
