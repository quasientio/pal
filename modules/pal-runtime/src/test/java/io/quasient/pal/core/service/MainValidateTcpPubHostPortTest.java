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
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;

public class MainValidateTcpPubHostPortTest {

  private static void setField(Object target, String field, Object value) throws Exception {
    Field f = Main.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Object getField(Object target, String field) throws Exception {
    Field f = Main.class.getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }

  private static void callValidateInput(Main m) throws Exception {
    Method validate = Main.class.getDeclaredMethod("validateInput");
    validate.setAccessible(true);
    validate.invoke(m);
  }

  @Test
  public void tcpPubHostPort_setsTcpOutPub_andAddsRunOption() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "tcpPub", "localhost:45678");
    callValidateInput(m);
    // add misc properties to populate out.pub
    Method addMisc = Main.class.getDeclaredMethod("addMiscProperties");
    addMisc.setAccessible(true);
    addMisc.invoke(m);
    Properties p = (Properties) getField(m, "properties");
    String out = p.getProperty("out.pub");
    assertThat(out, is("tcp://localhost:45678"));
    EnumSet<?> ro = (EnumSet<?>) getField(m, "runOptions");
    assertThat(ro.toString().contains("WITH_TCP_PUB"), is(true));
  }
}
