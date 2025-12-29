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

import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;

public class RemoveTest {

  @Test
  public void runCommand_withoutFlags_printsUsageAndReturnsOne() throws Exception {
    Remove rm = new Remove();
    // inject palCommand returning NO_URL
    var pc = Pal.class.getDeclaredConstructor();
    pc.setAccessible(true);
    Pal pal = pc.newInstance();
    var palUrl = Pal.class.getDeclaredField("palDirectoryUrl");
    palUrl.setAccessible(true);
    palUrl.set(pal, PalDirectory.NO_URL);
    var parent = Remove.class.getDeclaredField("palCommand");
    parent.setAccessible(true);
    parent.set(rm, pal);

    // wire err/out to capture
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    var outF = AbstractPalSubcommand.class.getDeclaredField("out");
    outF.setAccessible(true);
    outF.set(rm, new PrintStream(bout));

    // go through call() pipeline
    int code = rm.call();
    assertThat(code, is(1));
    assertThat(bout.toString(UTF_8), containsString("Use -L/--logs to remove logs"));
  }
}
