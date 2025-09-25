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

import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MainJmxAddressTest {

  private String pJmxRemote;
  private String pPort;
  private String pHost;
  private String pLocalOnly;

  @Before
  public void snapshotProps() {
    pJmxRemote = System.getProperty("com.sun.management.jmxremote");
    pPort = System.getProperty("com.sun.management.jmxremote.port");
    pHost = System.getProperty("java.rmi.server.hostname");
    pLocalOnly = System.getProperty("com.sun.management.jmxremote.local.only");
  }

  @After
  public void restoreProps() {
    setOrClear("com.sun.management.jmxremote", pJmxRemote);
    setOrClear("com.sun.management.jmxremote.port", pPort);
    setOrClear("java.rmi.server.hostname", pHost);
    setOrClear("com.sun.management.jmxremote.local.only", pLocalOnly);
  }

  private static void setOrClear(String k, String v) {
    if (v == null) System.clearProperty(k);
    else System.setProperty(k, v);
  }

  private static String callGetJmxAddress(Main m) throws Exception {
    Method g = Main.class.getDeclaredMethod("getJmxAddress");
    g.setAccessible(true);
    Object out = g.invoke(m);
    return (String) out;
  }

  @Test
  public void disabledProperty_returnsNull() throws Exception {
    System.setProperty("com.sun.management.jmxremote", "false");
    Main m = new Main();
    assertThat(callGetJmxAddress(m) == null, is(true));
  }

  @Test
  public void portAndHost_fromProps_returnsAddress() throws Exception {
    System.clearProperty("com.sun.management.jmxremote");
    System.setProperty("com.sun.management.jmxremote.port", "12345");
    System.setProperty("java.rmi.server.hostname", "example");
    Main m = new Main();
    assertThat(callGetJmxAddress(m), is("example:12345"));
  }

  @Test
  public void invalidPort_returnsNull() throws Exception {
    System.clearProperty("com.sun.management.jmxremote");
    System.setProperty("com.sun.management.jmxremote.port", "abc");
    System.setProperty("java.rmi.server.hostname", "host");
    Main m = new Main();
    assertThat(callGetJmxAddress(m) == null, is(true));
  }

  @Test
  public void localOnlyDefaultHost_returnsLocalhost() throws Exception {
    System.clearProperty("com.sun.management.jmxremote");
    System.setProperty("com.sun.management.jmxremote.port", "22222");
    System.clearProperty("java.rmi.server.hostname");
    System.clearProperty("com.sun.management.jmxremote.local.only");
    Main m = new Main();
    assertThat(callGetJmxAddress(m), is("localhost:22222"));
  }
}
