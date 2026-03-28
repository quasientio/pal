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
