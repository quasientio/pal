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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;

public class MainValidatePropsTest {

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
    Method addMisc = Main.class.getDeclaredMethod("addMiscProperties");
    addMisc.setAccessible(true);
    addMisc.invoke(m);
  }

  @Test
  public void validate_tcpPub_setsTcpOutPubProperty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "tcpPub", "127.0.0.1:45679");
    // set runOptions-indifferent fields and props; no System.exit triggered
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String out = p.getProperty("out.pub");
    assertThat(out, containsString("tcp://127.0.0.1:45679"));
  }

  @Test
  public void validate_jsonRpc_setsWsAddressAndRunOption() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "jsonRpc", "127.0.0.1:8080");
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String addr = p.getProperty("in.json.rpc");
    assertThat(addr, is("ws://127.0.0.1:8080"));
    // runOptions should contain WITH_JSON_RPC; reflect it and check string form
    EnumSet<?> ro = (EnumSet<?>) getField(m, "runOptions");
    assertThat(ro.toString().contains("WITH_JSON_RPC"), is(true));
  }

  @Test
  public void validate_noTcpPub_setsInprocOutPubProperty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    // leave tcpPub null
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String out = p.getProperty("out.pub");
    assertThat(out, containsString("inproc://"));
  }
}
