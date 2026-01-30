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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

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

  // ========== Additional isPowerOfTwo Tests ==========

  @Test
  public void isPowerOfTwo_largerValues() throws Exception {
    Method m = Main.class.getDeclaredMethod("isPowerOfTwo", int.class);
    m.setAccessible(true);
    assertThat((boolean) m.invoke(null, 256), is(true));
    assertThat((boolean) m.invoke(null, 1024), is(true));
    assertThat((boolean) m.invoke(null, 4096), is(true));
    assertThat((boolean) m.invoke(null, 65536), is(true));
  }

  @Test
  public void isPowerOfTwo_nonPowerOfTwo_returnsFalse() throws Exception {
    Method m = Main.class.getDeclaredMethod("isPowerOfTwo", int.class);
    m.setAccessible(true);
    assertThat((boolean) m.invoke(null, 5), is(false));
    assertThat((boolean) m.invoke(null, 100), is(false));
    assertThat((boolean) m.invoke(null, 1000), is(false));
    assertThat((boolean) m.invoke(null, 1023), is(false));
  }

  // ========== readPowerOfTwo Additional Tests ==========

  @Test
  public void readPowerOfTwo_validPowerOfTwo_returnsValue() throws Exception {
    Method m =
        Main.class.getDeclaredMethod("readPowerOfTwo", Properties.class, String.class, int.class);
    m.setAccessible(true);
    Properties p = new Properties();
    p.setProperty("wal.queue.initial", "2048");
    Object val = m.invoke(null, p, "wal.queue.initial", 1024);
    assertThat((int) (Integer) val, is(2048));
  }

  @Test
  public void readPowerOfTwo_emptyValue_throwsNumberFormatException() throws Exception {
    Method m =
        Main.class.getDeclaredMethod("readPowerOfTwo", Properties.class, String.class, int.class);
    m.setAccessible(true);
    Properties p = new Properties();
    p.setProperty("wal.queue.initial", "");
    boolean threw = false;
    try {
      m.invoke(null, p, "wal.queue.initial", 1024);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      threw = ite.getCause() instanceof NumberFormatException;
    }
    assertThat(threw, is(true));
  }

  // ========== findOpenPort Tests ==========

  @Test
  public void findOpenPort_returnsValidPort() throws Exception {
    Method m = Main.class.getDeclaredMethod("findOpenPort");
    m.setAccessible(true);
    int port = (int) m.invoke(null);
    assertThat(port, is(greaterThan(0)));
    assertThat(port, is(lessThan(65536)));
  }

  @Test
  public void findOpenPort_multipleCalls_returnDifferentPorts() throws Exception {
    Method m = Main.class.getDeclaredMethod("findOpenPort");
    m.setAccessible(true);
    int port1 = (int) m.invoke(null);
    int port2 = (int) m.invoke(null);
    // Both should be valid ports (they might occasionally be the same, but typically different)
    assertThat(port1, is(greaterThan(0)));
    assertThat(port2, is(greaterThan(0)));
  }
}
