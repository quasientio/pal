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

import static org.junit.Assert.fail;

import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import java.lang.reflect.Field;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@code --thread-affinity} CLI option in {@link Caller}.
 *
 * <p>Verifies that the thread affinity option is correctly parsed from the command line, defaults
 * to null when not specified, and is propagated to both {@link ExecMessage} and {@link
 * JsonRpcRequest} when building call requests via {@code StaticMethodCallBuilder}.
 */
public class CallerThreadAffinityTest {

  // ===========================================================================
  // Helper methods (same patterns as CallerTest)
  // ===========================================================================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object on which to set the field
   * @param fieldName the name of the field to set
   * @param value the value to set
   */
  @SuppressWarnings("UnusedMethod") // Will be used when #749 is implemented
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Gets a field value from an object via reflection, searching the class hierarchy.
   *
   * @param target the object from which to read the field
   * @param fieldName the name of the field to read
   * @return the field value
   */
  @SuppressWarnings("UnusedMethod") // Will be used when #749 is implemented
  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(target);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Locates the inner class {@code StaticMethodCallBuilder} within {@link Caller}.
   *
   * @return the inner class
   */
  @SuppressWarnings("UnusedMethod") // Will be used when #749 is implemented
  private static Class<?> findStaticMethodCallBuilder() {
    for (Class<?> cl : Caller.class.getDeclaredClasses()) {
      if (cl.getSimpleName().equals("StaticMethodCallBuilder")) {
        return cl;
      }
    }
    throw new AssertionError("StaticMethodCallBuilder inner class not found in Caller");
  }

  // ===========================================================================
  // Tests for --thread-affinity CLI option
  // ===========================================================================

  /**
   * Tests that the {@code --thread-affinity} option is correctly parsed and stored on the Caller
   * instance.
   *
   * <p>Acceptance criterion: [TEST:CallerThreadAffinityTest.threadAffinityOptionParsed]
   */
  @Test
  @Ignore("Awaiting implementation in #749")
  public void threadAffinityOptionParsed() throws Exception {
    // Given: A Caller command instance
    // When: The threadAffinity field is set to "fx-thread" (simulating --thread-affinity fx-thread)
    // Then: The threadAffinity field should equal "fx-thread"

    // TODO(#749): Implement test logic
    // Caller c = new Caller();
    // setField(c, "threadAffinity", "fx-thread");
    // assertThat(getField(c, "threadAffinity"), is("fx-thread"));
    fail("Not yet implemented");
  }

  /**
   * Tests that the {@code threadAffinity} field defaults to {@code null} when the {@code
   * --thread-affinity} option is not provided.
   *
   * <p>Acceptance criterion: [TEST:CallerThreadAffinityTest.threadAffinityDefaultIsNull]
   */
  @Test
  @Ignore("Awaiting implementation in #749")
  public void threadAffinityDefaultIsNull() throws Exception {
    // Given: A Caller command instance with default options
    // When: No --thread-affinity option is provided
    // Then: The threadAffinity field should be null

    // TODO(#749): Implement test logic
    // Caller c = new Caller();
    // assertThat(getField(c, "threadAffinity"), is(nullValue()));
    fail("Not yet implemented");
  }

  /**
   * Tests that the thread affinity value is propagated to the {@link ExecMessage} when {@code
   * StaticMethodCallBuilder.buildExecMessage()} is called.
   *
   * <p>Acceptance criterion: [TEST:CallerThreadAffinityTest.threadAffinityAppliedToExecMessage]
   */
  @Test
  @Ignore("Awaiting implementation in #749")
  public void threadAffinityAppliedToExecMessage() throws Exception {
    // Given: A Caller with --thread-affinity fx-thread and a class method call
    // When: buildExecMessage() is called on StaticMethodCallBuilder
    // Then: The resulting ExecMessage has threadAffinity == "fx-thread"

    // TODO(#749): Implement test logic
    // Caller c = new Caller();
    // setField(c, "threadAffinity", "fx-thread");
    // UUID peer = UUID.randomUUID();
    //
    // Class<?> inner = findStaticMethodCallBuilder();
    // Constructor<?> cons = inner.getDeclaredConstructor(
    //     Caller.class, UUID.class, String.class, String.class, List.class);
    // cons.setAccessible(true);
    // Object builder = cons.newInstance(c, peer, "com.example.App", "start", List.of());
    //
    // Method buildExec = builder.getClass().getDeclaredMethod("buildExecMessage");
    // ExecMessage em = (ExecMessage) buildExec.invoke(builder);
    //
    // assertThat(em.getThreadAffinity(), is("fx-thread"));
    fail("Not yet implemented");
  }

  /**
   * Tests that the thread affinity value is propagated to the {@link JsonRpcRequest} params when
   * {@code StaticMethodCallBuilder.buildJsonRpc()} is called.
   *
   * <p>Acceptance criterion: [TEST:CallerThreadAffinityTest.threadAffinityAppliedToJsonRpcRequest]
   */
  @Test
  @Ignore("Awaiting implementation in #749")
  public void threadAffinityAppliedToJsonRpcRequest() throws Exception {
    // Given: A Caller with --thread-affinity fx-thread and JSON-RPC mode
    // When: buildJsonRpc() is called on StaticMethodCallBuilder
    // Then: The resulting JsonRpcRequest params has threadAffinity == "fx-thread"

    // TODO(#749): Implement test logic
    // Caller c = new Caller();
    // setField(c, "threadAffinity", "fx-thread");
    // UUID peer = UUID.randomUUID();
    //
    // Class<?> inner = findStaticMethodCallBuilder();
    // Constructor<?> cons = inner.getDeclaredConstructor(
    //     Caller.class, UUID.class, String.class, String.class, List.class);
    // cons.setAccessible(true);
    // Object builder = cons.newInstance(c, peer, "com.example.App", "start", List.of());
    //
    // Method buildJson = builder.getClass().getDeclaredMethod("buildJsonRpc");
    // JsonRpcRequest request = (JsonRpcRequest) buildJson.invoke(builder);
    //
    // assertThat(request.getParams().getThreadAffinity(), is("fx-thread"));
    fail("Not yet implemented");
  }
}
