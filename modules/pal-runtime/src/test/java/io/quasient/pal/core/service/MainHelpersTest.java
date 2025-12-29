/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.Test;
import picocli.CommandLine;

public class MainHelpersTest {

  @Test
  public void isPowerOfTwo_cases() throws Exception {
    Method m = Main.class.getDeclaredMethod("isPowerOfTwo", int.class);
    m.setAccessible(true);
    assertThat((boolean) m.invoke(null, 1), is(true));
    assertThat((boolean) m.invoke(null, 2), is(true));
    assertThat((boolean) m.invoke(null, 3), is(false));
    assertThat((boolean) m.invoke(null, 0), is(false));
    assertThat((boolean) m.invoke(null, -2), is(false));
  }

  @Test
  public void readPowerOfTwo_defaultAndRejectsNonPowerOfTwo() throws Exception {
    Method m =
        Main.class.getDeclaredMethod("readPowerOfTwo", Properties.class, String.class, int.class);
    m.setAccessible(true);
    Properties p = new Properties();
    // default returned when key absent
    Object val = m.invoke(null, p, "wal.queue.initial", 1024);
    assertThat((int) (Integer) val, is(1024));

    // non power-of-two → throws
    p.setProperty("wal.queue.initial", "1000");
    boolean threw = false;
    try {
      m.invoke(null, p, "wal.queue.initial", 1024);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      threw = ite.getCause() instanceof IllegalArgumentException;
    }
    assertThat(threw, is(true));
  }

  @Test
  public void picocli_help_executesWithoutCallingCallable() {
    int exit = new CommandLine(new Main()).execute("--help");
    assertThat(exit, is(0));
  }
}
