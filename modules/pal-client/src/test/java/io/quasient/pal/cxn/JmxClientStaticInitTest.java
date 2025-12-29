/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class JmxClientStaticInitTest {

  @Test
  public void staticBlockDisablesRmiCodebaseDownload() throws Exception {
    System.clearProperty("com.sun.jndi.rmi.object.trustURLCodebase");
    // Force class initialization
    Class.forName(JmxClient.class.getName());
    assertThat(System.getProperty("com.sun.jndi.rmi.object.trustURLCodebase"), is("false"));
  }
}
