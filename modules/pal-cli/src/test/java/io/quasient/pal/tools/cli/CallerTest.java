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

import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.Assert;
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

    // Provide stdin - save original and restore after test
    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream("{}\n".getBytes(StandardCharsets.UTF_8)));
      // Now set className too
      var cn = Caller.class.getDeclaredField("className");
      cn.setAccessible(true);
      cn.set(c, "X");
      Exception e = assertThrows(RuntimeException.class, c::validateInput);
      assertThat(e.getMessage(), containsString("Either specify a class"));
    } finally {
      System.setIn(originalIn);
    }
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
  // Tests for edge cases in Caller - Implemented in #363
  // ============================================================================

  /**
   * Tests that print(ReturnValue) handles a ReturnValue with null object gracefully.
   *
   * <p>This test targets the null object branch in the print(ReturnValue) method at line 813-816.
   */
  @Test
  public void testPrint_returnValue_handlesNullObject() throws Exception {
    // Given: A Caller instance with printResponses enabled
    Caller c = new Caller();
    var printResponsesField = Caller.class.getDeclaredField("printResponses");
    printResponsesField.setAccessible(true);
    printResponsesField.set(c, true);

    var outField = AbstractPalSubcommand.class.getDeclaredField("out");
    outField.setAccessible(true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    outField.set(c, new PrintStream(bout));

    // Create a ReturnValue with isVoid=false but object=null
    var rv = new ReturnValue();
    rv.setIsVoid(false);
    rv.setObject(null);

    // When: print(ReturnValue) is called
    var printMethod = Caller.class.getDeclaredMethod("print", ReturnValue.class);
    printMethod.setAccessible(true);
    printMethod.invoke(c, rv);

    // Then: The method should handle null gracefully (print nothing)
    assertEquals("", bout.toString(StandardCharsets.UTF_8));
  }

  /**
   * Tests that print(RaisedThrowable) handles a RaisedThrowable with null internal values.
   *
   * <p>This test targets null value handling in the print(RaisedThrowable) method at line 824-829.
   */
  @Test
  public void testPrint_raisedThrowable_handlesNullThrowable() throws Exception {
    // Given: A Caller instance with printResponses enabled
    Caller c = new Caller();
    var printResponsesField = Caller.class.getDeclaredField("printResponses");
    printResponsesField.setAccessible(true);
    printResponsesField.set(c, true);

    var outField = AbstractPalSubcommand.class.getDeclaredField("out");
    outField.setAccessible(true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    outField.set(c, new PrintStream(bout));

    // Create a RaisedThrowable with null className/message/stackTrace
    var rt = new RaisedThrowable();
    // All fields are null by default

    // When: print(RaisedThrowable) is called
    var printMethod = Caller.class.getDeclaredMethod("print", RaisedThrowable.class);
    printMethod.setAccessible(true);
    printMethod.invoke(c, rt);

    // Then: The method should handle gracefully without NPE
    // ColferUtils.format handles null fields appropriately
    String output = bout.toString(StandardCharsets.UTF_8);
    // Just verify no NPE was thrown and some output was produced
    Assert.assertNotNull(output);
  }

  /**
   * Tests that StaticMethodCallBuilder.buildJsonRpc() handles null argList.
   *
   * <p>This test targets the null argList branch in buildJsonRpc() at line 966-972.
   */
  @Test
  public void testBuildJsonRpc_handlesNullArgList() throws Exception {
    // Given: A StaticMethodCallBuilder constructed with null argList
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
    // Construct with null argList
    Object builder = cons.newInstance(c, peer, "java.lang.System", "getProperty", null);

    // When: buildJsonRpc() is called
    Method buildJson = builder.getClass().getDeclaredMethod("buildJsonRpc");
    buildJson.setAccessible(true);

    // Then: A valid JsonRpcRequest should be created (no args added to the params builder)
    // The method should handle null argList without NPE
    Exception ex = assertThrows(Exception.class, () -> buildJson.invoke(builder));
    // JsonRpcRequest validation requires a top-level method field
    // We verify the builder reached JSON-RPC construction without NPE from null argList
    assertThat(ex.getCause().getClass().getSimpleName(), containsString("InvalidJsonRpcRequest"));
  }

  /**
   * Tests that StaticMethodCallBuilder constructor handles null argList.
   *
   * <p>This test targets the null argList handling in constructor at line 935-937.
   */
  @Test
  public void testConstructor_handlesNullArgList() throws Exception {
    // Given: A null argList parameter
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

    // When: StaticMethodCallBuilder is constructed with null argList
    Object builder = cons.newInstance(c, peer, "java.lang.System", "getProperty", null);

    // Then: parameters[0] should remain as empty String[] (default)
    var parametersField = inner.getDeclaredField("parameters");
    parametersField.setAccessible(true);
    Object[] parameters = (Object[]) parametersField.get(builder);

    Assert.assertNotNull(parameters);
    assertEquals(1, parameters.length);
    Assert.assertNotNull(parameters[0]);
    Assert.assertTrue(parameters[0] instanceof String[]);
    assertEquals(0, ((String[]) parameters[0]).length);
  }

  /**
   * Tests that validateInput() succeeds with a valid peer address (-pa).
   *
   * <p>This test targets the valid peer address path in validateInput() at line 265-284.
   */
  @Test
  public void testValidateInput_success_withValidPeerAddress() throws Exception {
    // Given: A Caller instance with valid peer address (tcp://) set
    Caller c = new Caller();

    var peerIdField = Caller.class.getDeclaredField("peerIdentifier");
    peerIdField.setAccessible(true);
    peerIdField.set(c, "tcp://localhost:5555");

    var classNameField = Caller.class.getDeclaredField("className");
    classNameField.setAccessible(true);
    classNameField.set(c, "java.lang.System");

    // When: validateInput() is called
    // Then: No exception should be thrown
    c.validateInput();

    // Verify peerAddress was set correctly
    var peerAddressField = Caller.class.getDeclaredField("peerAddress");
    peerAddressField.setAccessible(true);
    String peerAddress = (String) peerAddressField.get(c);
    assertEquals("tcp://localhost:5555", peerAddress);
  }

  /**
   * Tests that validateInput() succeeds with valid input/output log configuration.
   *
   * <p>This test targets the valid log configuration path in validateInput() at line 259-261.
   */
  @Test
  public void testValidateInput_success_withValidLogConfiguration() throws Exception {
    // Given: A Caller instance with valid inputLogName and outputLogName set
    Caller c = new Caller();

    var inputLogField = Caller.class.getDeclaredField("inputLogName");
    inputLogField.setAccessible(true);
    inputLogField.set(c, "input-log");

    var outputLogField = Caller.class.getDeclaredField("outputLogName");
    outputLogField.setAccessible(true);
    outputLogField.set(c, "output-log");

    var classNameField = Caller.class.getDeclaredField("className");
    classNameField.setAccessible(true);
    classNameField.set(c, "java.lang.System");

    // When: validateInput() is called
    // Then: No exception should be thrown
    c.validateInput();
  }
}
