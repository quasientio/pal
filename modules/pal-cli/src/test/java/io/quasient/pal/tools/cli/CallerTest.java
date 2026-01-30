/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Ignore;
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
      if (cl.getSimpleName().equals("StaticMethodCallBuilder")) {
        inner = cl;
        break;
      }
    }
    Assert.assertNotNull(inner);
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

  // ============================================================================
  // Test specifications for issue #362 - Awaiting implementation in #363
  // ============================================================================

  /**
   * Tests that print(ReturnValue) handles a ReturnValue with null object gracefully.
   *
   * <p>This test targets the null object branch in the print(ReturnValue) method at line 813-816.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testPrint_returnValue_handlesNullObject() throws Exception {
    // Given: A Caller instance with printResponses enabled
    //        and a ReturnValue that has isVoid=false but object=null

    // When: print(ReturnValue) is called with the ReturnValue containing null object

    // Then: The method should handle null gracefully without NPE
    //       (either print nothing or print appropriate message)

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that print(RaisedThrowable) handles a RaisedThrowable with null internal values.
   *
   * <p>This test targets null value handling in the print(RaisedThrowable) method at line 824-829.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testPrint_raisedThrowable_handlesNullThrowable() throws Exception {
    // Given: A Caller instance with printResponses enabled
    //        and a RaisedThrowable with null className/message/stackTrace

    // When: print(RaisedThrowable) is called

    // Then: The method should return early or handle gracefully without NPE
    //       ColferUtils.format should handle null fields appropriately

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that StaticMethodCallBuilder.buildJsonRpc() handles null argList.
   *
   * <p>This test targets the null argList branch in buildJsonRpc() at line 966-972.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testBuildJsonRpc_handlesNullArgList() throws Exception {
    // Given: A StaticMethodCallBuilder constructed with null argList

    // When: buildJsonRpc() is called

    // Then: A valid JsonRpcRequest should be created with empty params
    //       (no args added to the params builder)

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that StaticMethodCallBuilder constructor handles null argList.
   *
   * <p>This test targets the null argList handling in constructor at line 935-937.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testConstructor_handlesNullArgList() throws Exception {
    // Given: A null argList parameter

    // When: StaticMethodCallBuilder is constructed with null argList

    // Then: argList should be handled gracefully - either initialized to empty
    //       or parameters[0] should remain as empty String[]

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that validateInput() succeeds with a valid peer address (-pa).
   *
   * <p>This test targets the valid peer address path in validateInput() at line 265-284.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testValidateInput_success_withValidPeerAddress() throws Exception {
    // Given: A Caller instance with valid peer address (tcp:// or ws://) set
    //        and a className set (so we have something to call)

    // When: validateInput() is called

    // Then: No exception should be thrown
    //       peerAddress should be set to the provided address

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }

  /**
   * Tests that validateInput() succeeds with valid input/output log configuration.
   *
   * <p>This test targets the valid log configuration path in validateInput() at line 259-261.
   */
  @Test
  @Ignore("Awaiting implementation in #363")
  public void testValidateInput_success_withValidLogConfiguration() throws Exception {
    // Given: A Caller instance with valid inputLogName and outputLogName set
    //        and a className set (so we have something to call)
    //        or sendAndForget=true (so inputLogName is not required)

    // When: validateInput() is called

    // Then: No exception should be thrown

    // TODO(#363): Implement after implementation is provided
    fail("Not yet implemented");
  }
}
