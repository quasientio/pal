/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.service;

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
