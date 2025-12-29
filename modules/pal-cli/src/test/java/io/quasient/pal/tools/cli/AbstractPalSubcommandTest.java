/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;

public class AbstractPalSubcommandTest {

  private static class FailingCmd extends AbstractPalSubcommand {
    @Override
    protected void validateInput() {
      throw new RuntimeException("bad input");
    }

    @Override
    protected void initialize() {}

    @Override
    protected int runCommand() {
      return 0;
    }
  }

  @Test
  public void call_whenValidateFails_printsErrorAndReturnsOne() throws Exception {
    FailingCmd cmd = new FailingCmd();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    // inject custom err stream
    var f = AbstractPalSubcommand.class.getDeclaredField("err");
    f.setAccessible(true);
    f.set(cmd, new PrintStream(err));

    int code = cmd.call();
    assertThat(code, is(1));
    assertThat(err.toString(UTF_8), containsString("bad input"));
  }
}
