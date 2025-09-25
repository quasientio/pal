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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import com.quasient.pal.common.cli.PalCommand;
import java.lang.reflect.Field;
import org.junit.Test;
import picocli.CommandLine;

public class MainCallTest {

  @Test
  public void call_withDummyMainClass_returnsDefaultExit() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    // Provide only the target class; no services enabled
    cl.parseArgs("com.quasient.pal.core.service.testdata.DummyMain");
    // Avoid env-provided <pal_directory> by overriding parent PalCommand to return empty
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(SelfBootstrapInvoker.DEFAULT_EXIT_VALUE));
  }

  @Test
  public void call_withDisableAnnotationProcessing_alsoReturnsDefaultExit() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    cl.parseArgs(
        "--disable-annotation-processing", "com.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(SelfBootstrapInvoker.DEFAULT_EXIT_VALUE));
  }

  @Test
  public void call_setsDefaultQueueProperties() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    cl.parseArgs("com.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(SelfBootstrapInvoker.DEFAULT_EXIT_VALUE));

    // Reflect properties and assert some defaults populated by validateProperties
    java.lang.reflect.Field f = Main.class.getDeclaredField("properties");
    f.setAccessible(true);
    java.util.Properties p = (java.util.Properties) f.get(app);
    // Defaults from Main: wal/pub queues and pub spsc size
    assertNotNull(p.getProperty("wal.queue.initial"));
    assertNotNull(p.getProperty("wal.queue.chunk"));
    assertNotNull(p.getProperty("wal.queue.max"));
    assertNotNull(p.getProperty("pub.queue.initial"));
    assertNotNull(p.getProperty("pub.queue.chunk"));
    assertNotNull(p.getProperty("pub.queue.max"));
    assertNotNull(p.getProperty("pub.spsc_size"));
  }

  private static void setParentCommandToEmpty(Main app) throws Exception {
    PalCommand dummy = () -> ""; // empty string forces Main to treat as no paldir
    Field pf = Main.class.getDeclaredField("palCommand");
    pf.setAccessible(true);
    pf.set(app, dummy);
  }
}
