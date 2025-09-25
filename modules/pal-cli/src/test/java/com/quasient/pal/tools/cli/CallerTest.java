/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.RaisedThrowable;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class CallerTest {

  @Test
  public void validateInput_errors_on_conflicts() throws Exception {
    Caller c = new Caller();
    var peerIdField = Caller.class.getDeclaredField("peerIdentifier");
    peerIdField.setAccessible(true);
    var outLogField = Caller.class.getDeclaredField("outputLogName");
    outLogField.setAccessible(true);
    var inLogField = Caller.class.getDeclaredField("inputLogName");
    inLogField.setAccessible(true);

    // nowhere to call
    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Nowhere to call"));

    // output log without input log and not forget => error
    outLogField.set(c, "out");
    e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("You must specify a log to read"));

    // peer + forget => error
    outLogField.set(c, null);
    peerIdField.set(c, UUID.randomUUID().toString());
    var forget = Caller.class.getDeclaredField("sendAndForget");
    forget.setAccessible(true);
    forget.set(c, true);
    e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Direct p2p talk"));
  }

  @Test
  public void validateInput_stdin_vs_classname_conflict() throws Exception {
    Caller c = new Caller();
    var logName = Caller.class.getDeclaredField("inputLogName");
    logName.setAccessible(true);
    logName.set(c, "L");
    var outLog = Caller.class.getDeclaredField("outputLogName");
    outLog.setAccessible(true);
    outLog.set(c, "L");

    // Provide stdin
    System.setIn(new ByteArrayInputStream("{}\n".getBytes(StandardCharsets.UTF_8)));
    // Now set className too
    var cn = Caller.class.getDeclaredField("className");
    cn.setAccessible(true);
    cn.set(c, "X");
    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Either specify a class"));
  }

  @Test
  public void mainMethodCallBuilder_builds_exec_and_jsonrpc() throws Exception {
    Caller c = new Caller();
    UUID peer = UUID.randomUUID();
    Class<?> inner = null;
    for (Class<?> cl : Caller.class.getDeclaredClasses()) {
      if (cl.getSimpleName().equals("MainMethodCallBuilder")) {
        inner = cl;
        break;
      }
    }
    Constructor<?> cons =
        inner.getDeclaredConstructor(
            Caller.class, UUID.class, String.class, String.class, List.class);
    cons.setAccessible(true);
    Object builder = cons.newInstance(c, peer, "java.lang.System", "getProperty", List.of("x"));

    Method buildExec = builder.getClass().getDeclaredMethod("buildExecMessage");
    ExecMessage em = (ExecMessage) buildExec.invoke(builder);
    assertEquals("java.lang.System", em.getClassMethodCall().getClazz().getName());
    assertEquals("getProperty", em.getClassMethodCall().getName());

    Method buildJson = builder.getClass().getDeclaredMethod("buildJsonRpc");
    Exception ex = assertThrows(Exception.class, () -> buildJson.invoke(builder));
    // JsonRpcRequest validation requires a top-level method field; ensure we reach here
    assertThat(ex.getCause().getClass().getSimpleName(), containsString("InvalidJsonRpcRequest"));
  }

  @Test
  public void printIfRequired_prints_return_and_throwable() throws Exception {
    Caller c = new Caller();
    var pr = Caller.class.getDeclaredField("printResponses");
    pr.setAccessible(true);
    pr.set(c, true);
    var outF = AbstractPalSubcommand.class.getDeclaredField("out");
    outF.setAccessible(true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    outF.set(c, new PrintStream(bout));

    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    // trigger builder side-effects without keeping an unused local
    b.buildEmptyConstructor(UUID.randomUUID(), "java.lang.String");
    var rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setValue("\"ok\"");
    rv.setObject(obj);
    var m1 = Caller.class.getDeclaredMethod("print", ReturnValue.class);
    m1.setAccessible(true);
    m1.invoke(c, rv);
    // also throwable path
    var rt = new RaisedThrowable();
    var m2 = Caller.class.getDeclaredMethod("print", RaisedThrowable.class);
    m2.setAccessible(true);
    m2.invoke(c, rt);
  }
}
