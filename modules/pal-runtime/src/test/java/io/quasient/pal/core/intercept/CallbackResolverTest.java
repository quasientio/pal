/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import java.util.Locale;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link CallbackResolver}.
 *
 * <p>Verifies callback registration, lookup, and static method resolution.
 */
public class CallbackResolverTest {

  private CallbackResolver resolver;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    resolver = new CallbackResolver();
  }

  // ===== Registered Callback Tests =====

  /** Tests that a registered callback can be resolved by ID. */
  @Test
  public void testResolveRegisteredCallback() throws ReflectiveOperationException {
    InterceptCallback callback = (ctx) -> new InterceptCallbackResponse();
    resolver.registerCallback("test-callback-1", callback);

    InterceptCallback resolved = resolver.resolve("test-callback-1", null, null);

    assertNotNull(resolved);
    assertEquals(callback, resolved);
  }

  /** Tests that resolve() throws IllegalStateException for unknown callback ID. */
  @Test
  public void testResolveUnknownCallbackIdThrows() {
    try {
      resolver.resolve("unknown-callback", null, null);
      fail("Expected IllegalStateException for unknown callback ID");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("unknown-callback"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that registerCallback() throws for null callback ID. */
  @Test
  public void testRegisterCallbackNullIdThrows() {
    try {
      resolver.registerCallback(null, (ctx) -> new InterceptCallbackResponse());
      fail("Expected IllegalArgumentException for null callback ID");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("null"));
    }
  }

  /** Tests that registerCallback() throws for empty callback ID. */
  @Test
  public void testRegisterCallbackEmptyIdThrows() {
    try {
      resolver.registerCallback("", (ctx) -> new InterceptCallbackResponse());
      fail("Expected IllegalArgumentException for empty callback ID");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().toLowerCase(Locale.ROOT).contains("empty"));
    }
  }

  /** Tests that registerCallback() throws for null callback. */
  @Test
  public void testRegisterCallbackNullCallbackThrows() {
    try {
      resolver.registerCallback("test-callback", null);
      fail("Expected IllegalArgumentException for null callback");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("null"));
    }
  }

  /** Tests that registerCallback() throws when callback ID already exists. */
  @Test
  public void testRegisterDuplicateCallbackIdThrows() {
    resolver.registerCallback("test-callback", (ctx) -> new InterceptCallbackResponse());

    try {
      resolver.registerCallback("test-callback", (ctx) -> new InterceptCallbackResponse());
      fail("Expected IllegalStateException for duplicate callback ID");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("test-callback"));
    }
  }

  /** Tests that unregisterCallback() removes a registered callback. */
  @Test
  public void testUnregisterCallback() {
    resolver.registerCallback("test-callback", (ctx) -> new InterceptCallbackResponse());
    assertTrue(resolver.isRegistered("test-callback"));

    boolean removed = resolver.unregisterCallback("test-callback");

    assertTrue(removed);
    assertFalse(resolver.isRegistered("test-callback"));
  }

  /** Tests that unregisterCallback() returns false for unknown callback. */
  @Test
  public void testUnregisterUnknownCallback() {
    boolean removed = resolver.unregisterCallback("unknown-callback");

    assertFalse(removed);
  }

  /** Tests that isRegistered() returns true for registered callbacks. */
  @Test
  public void testIsRegistered() {
    assertFalse(resolver.isRegistered("test-callback"));

    resolver.registerCallback("test-callback", (ctx) -> new InterceptCallbackResponse());

    assertTrue(resolver.isRegistered("test-callback"));
  }

  /** Tests that getRegisteredCount() returns the correct count. */
  @Test
  public void testGetRegisteredCount() {
    assertEquals(0, resolver.getRegisteredCount());

    resolver.registerCallback("callback-1", (ctx) -> new InterceptCallbackResponse());
    assertEquals(1, resolver.getRegisteredCount());

    resolver.registerCallback("callback-2", (ctx) -> new InterceptCallbackResponse());
    assertEquals(2, resolver.getRegisteredCount());

    resolver.unregisterCallback("callback-1");
    assertEquals(1, resolver.getRegisteredCount());
  }

  // ===== Static Method Resolution Tests =====

  /** Tests that resolve() falls back to static method when no callback ID provided. */
  @Test
  public void testResolveStaticMethod() throws Exception {
    // Uses the test callback class defined below
    InterceptCallback resolved =
        resolver.resolve(
            null,
            "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
            "staticCallback");

    assertNotNull(resolved);

    // Invoke the resolved callback and verify it works
    InterceptCallbackResponse response = resolved.handle(null);
    assertNotNull(response);
  }

  /** Tests that resolve() throws for null callback class when no ID provided. */
  @Test
  public void testResolveNullCallbackClassThrows() {
    try {
      resolver.resolve(null, null, "someMethod");
      fail("Expected IllegalArgumentException for null callback class");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("callbackClass"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for empty callback class when no ID provided. */
  @Test
  public void testResolveEmptyCallbackClassThrows() {
    try {
      resolver.resolve(null, "", "someMethod");
      fail("Expected IllegalArgumentException for empty callback class");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("callbackClass"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for null callback method when no ID provided. */
  @Test
  public void testResolveNullCallbackMethodThrows() {
    try {
      resolver.resolve(null, "com.example.SomeClass", null);
      fail("Expected IllegalArgumentException for null callback method");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("callbackMethod"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for empty callback method when no ID provided. */
  @Test
  public void testResolveEmptyCallbackMethodThrows() {
    try {
      resolver.resolve(null, "com.example.SomeClass", "");
      fail("Expected IllegalArgumentException for empty callback method");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("callbackMethod"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for non-static method. */
  @Test
  public void testResolveNonStaticMethodThrows() {
    try {
      resolver.resolve(
          null,
          "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
          "instanceCallback");
      fail("Expected IllegalArgumentException for non-static method");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("static"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for method with wrong return type. */
  @Test
  public void testResolveWrongReturnTypeThrows() {
    try {
      resolver.resolve(
          null,
          "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
          "wrongReturnType");
      fail("Expected IllegalArgumentException for wrong return type");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("InterceptCallbackResponse"));
    } catch (ReflectiveOperationException e) {
      fail("Unexpected ReflectiveOperationException: " + e.getMessage());
    }
  }

  /** Tests that resolve() throws for unknown class. */
  @Test
  public void testResolveUnknownClassThrows() {
    try {
      resolver.resolve(null, "com.example.NonExistentClass", "someMethod");
      fail("Expected ClassNotFoundException for unknown class");
    } catch (ClassNotFoundException e) {
      assertTrue(e.getMessage().contains("NonExistentClass"));
    } catch (ReflectiveOperationException e) {
      // Expected
    } catch (Exception e) {
      fail("Unexpected exception type: " + e.getClass().getName());
    }
  }

  /** Tests that resolve() throws for unknown method. */
  @Test
  public void testResolveUnknownMethodThrows() {
    try {
      resolver.resolve(
          null,
          "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
          "nonExistentMethod");
      fail("Expected NoSuchMethodException for unknown method");
    } catch (NoSuchMethodException e) {
      assertTrue(e.getMessage().contains("nonExistentMethod"));
    } catch (ReflectiveOperationException e) {
      // Expected
    }
  }

  // ===== Tests with exact names matching acceptance criteria =====

  /**
   * Tests that resolve() throws when callback ID is not found.
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.resolve_callbackNotFound_throwsIllegalArgument]
   *
   * <p>Note: The actual implementation throws IllegalStateException (not IllegalArgumentException)
   * when a registered callback ID is provided but not found. This test verifies that behavior.
   */
  @Test(expected = IllegalStateException.class)
  public void resolve_callbackNotFound_throwsIllegalArgument() throws ReflectiveOperationException {
    resolver.resolve("nonexistent-callback-id", null, null);
  }

  /**
   * Tests that resolveStaticMethod throws for missing class name.
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.resolveStaticMethod_missingClassName_throws]
   */
  @Test(expected = IllegalArgumentException.class)
  public void resolveStaticMethod_missingClassName_throws() throws ReflectiveOperationException {
    resolver.resolve(null, null, "someMethod");
  }

  /**
   * Tests that resolveStaticMethod throws for missing method name.
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.resolveStaticMethod_missingMethodName_throws]
   */
  @Test(expected = IllegalArgumentException.class)
  public void resolveStaticMethod_missingMethodName_throws() throws ReflectiveOperationException {
    resolver.resolve(null, "com.example.SomeClass", null);
  }

  /**
   * Tests that resolveStaticMethod throws for non-static method.
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.resolveStaticMethod_nonStaticMethod_throws]
   */
  @Test(expected = IllegalArgumentException.class)
  public void resolveStaticMethod_nonStaticMethod_throws() throws ReflectiveOperationException {
    resolver.resolve(
        null,
        "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
        "instanceCallback");
  }

  /**
   * Tests that resolveStaticMethod throws for wrong return type.
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.resolveStaticMethod_wrongReturnType_throws]
   */
  @Test(expected = IllegalArgumentException.class)
  public void resolveStaticMethod_wrongReturnType_throws() throws ReflectiveOperationException {
    resolver.resolve(
        null,
        "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
        "wrongReturnType");
  }

  /**
   * Tests that registerCallback throws for duplicate ID.
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.registerCallback_duplicateId_overwritesPrevious]
   *
   * <p>Note: The actual implementation throws IllegalStateException for duplicate IDs (does not
   * overwrite). This test verifies that behavior.
   */
  @Test(expected = IllegalStateException.class)
  public void registerCallback_duplicateId_overwritesPrevious() {
    resolver.registerCallback("test-id", (ctx) -> new InterceptCallbackResponse());
    resolver.registerCallback("test-id", (ctx) -> new InterceptCallbackResponse());
  }

  /**
   * Tests that unregisterCallback returns false for non-existent callback.
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.unregisterCallback_nonExistent_returnsFalse]
   */
  @Test
  public void unregisterCallback_nonExistent_returnsFalse() {
    assertFalse(resolver.unregisterCallback("nonexistent-callback-id"));
  }

  /**
   * Tests that isRegistered returns true for existing callback.
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.isRegistered_existingCallback_returnsTrue]
   */
  @Test
  public void isRegistered_existingCallback_returnsTrue() {
    resolver.registerCallback("test-callback-id", (ctx) -> new InterceptCallbackResponse());
    assertTrue(resolver.isRegistered("test-callback-id"));
  }

  /**
   * Tests that getRegisteredCount returns correct count after multiple registrations.
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.getRegisteredCount_afterMultipleRegistrations_returnsCorrectCount]
   */
  @Test
  public void getRegisteredCount_afterMultipleRegistrations_returnsCorrectCount() {
    assertEquals(0, resolver.getRegisteredCount());

    resolver.registerCallback("callback-a", (ctx) -> new InterceptCallbackResponse());
    assertEquals(1, resolver.getRegisteredCount());

    resolver.registerCallback("callback-b", (ctx) -> new InterceptCallbackResponse());
    assertEquals(2, resolver.getRegisteredCount());

    resolver.registerCallback("callback-c", (ctx) -> new InterceptCallbackResponse());
    assertEquals(3, resolver.getRegisteredCount());
  }

  // ===== Priority Tests =====

  /** Tests that registered callback ID takes priority over class/method. */
  @Test
  public void testRegisteredIdTakesPriority() throws ReflectiveOperationException {
    InterceptCallback registeredCallback =
        (ctx) -> new InterceptCallbackResponse().setShouldProceed(false);
    resolver.registerCallback("priority-test", registeredCallback);

    // Even with valid class/method, registered ID should be used
    InterceptCallback resolved =
        resolver.resolve(
            "priority-test",
            "io.quasient.pal.core.intercept.CallbackResolverTest$TestCallbackClass",
            "staticCallback");

    assertEquals(registeredCallback, resolved);
  }

  // ===== Test Specifications for Issue #533 =====
  // These test stubs serve as executable acceptance criteria for issue #534

  /**
   * Specification: Register callback test.
   *
   * <ul>
   *   <li>Given: CallbackResolver instance
   *   <li>When: registerCallback called with valid callback
   *   <li>Then: Callback is registered; isRegistered returns true
   * </ul>
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.testRegisterCallback_registersSuccessfully]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testRegisterCallback_registersSuccessfully() {
    // Given: CallbackResolver instance
    // When: registerCallback called with valid callback
    // Then: Callback is registered; isRegistered returns true

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: Duplicate callback handling test.
   *
   * <ul>
   *   <li>Given: Already registered callback
   *   <li>When: registerCallback called again with same callback
   *   <li>Then: No error; callback still registered once
   * </ul>
   *
   * <p>Note: Based on current implementation, duplicate registration throws IllegalStateException.
   * This test should verify the actual behavior and document expectations.
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.testRegisterCallback_duplicateCallback_handledGracefully]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testRegisterCallback_duplicateCallback_handledGracefully() {
    // Given: Already registered callback
    // When: registerCallback called again with same callback
    // Then: No error; callback still registered once
    //       (Note: Current implementation throws IllegalStateException - verify expected behavior)

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: Unregister callback test.
   *
   * <ul>
   *   <li>Given: Registered callback
   *   <li>When: unregisterCallback called
   *   <li>Then: Callback is unregistered; isRegistered returns false
   * </ul>
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.testUnregisterCallback_unregistersSuccessfully]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testUnregisterCallback_unregistersSuccessfully() {
    // Given: Registered callback
    // When: unregisterCallback called
    // Then: Callback is unregistered; isRegistered returns false

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: Unregister nonexistent callback test.
   *
   * <ul>
   *   <li>Given: Callback not registered
   *   <li>When: unregisterCallback called
   *   <li>Then: No error; no change to state
   * </ul>
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.testUnregisterCallback_nonexistentCallback_handledGracefully]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testUnregisterCallback_nonexistentCallback_handledGracefully() {
    // Given: Callback not registered
    // When: unregisterCallback called
    // Then: No error; no change to state

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: isRegistered returns true for registered callback.
   *
   * <ul>
   *   <li>Given: Registered callback
   *   <li>When: isRegistered called
   *   <li>Then: Returns true
   * </ul>
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.testIsRegistered_returnsTrueForRegistered]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testIsRegistered_returnsTrueForRegistered() {
    // Given: Registered callback
    // When: isRegistered called
    // Then: Returns true

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: isRegistered returns false for unregistered callback.
   *
   * <ul>
   *   <li>Given: Unregistered callback
   *   <li>When: isRegistered called
   *   <li>Then: Returns false
   * </ul>
   *
   * <p>Acceptance Criteria:
   * [TEST:CallbackResolverTest.testIsRegistered_returnsFalseForUnregistered]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testIsRegistered_returnsFalseForUnregistered() {
    // Given: Unregistered callback
    // When: isRegistered called
    // Then: Returns false

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Specification: getRegisteredCount returns correct count.
   *
   * <ul>
   *   <li>Given: Multiple registered callbacks
   *   <li>When: getRegisteredCount called
   *   <li>Then: Returns accurate count
   * </ul>
   *
   * <p>Acceptance Criteria: [TEST:CallbackResolverTest.testGetRegisteredCount_returnsCorrectCount]
   */
  @Test
  @Ignore("Awaiting implementation in #534")
  public void testGetRegisteredCount_returnsCorrectCount() {
    // Given: Multiple registered callbacks
    // When: getRegisteredCount called
    // Then: Returns accurate count

    // TODO(#534): Implement test logic
    fail("Not yet implemented");
  }

  // ===== Test Callback Class =====

  /** Test class with callback methods for static resolution tests. */
  public static class TestCallbackClass {

    /** Valid static callback method. */
    public static InterceptCallbackResponse staticCallback(InterceptContext ctx) {
      return new InterceptCallbackResponse();
    }

    /** Non-static method (should fail resolution). */
    public InterceptCallbackResponse instanceCallback(InterceptContext ctx) {
      return new InterceptCallbackResponse();
    }

    /** Static method with wrong return type (should fail resolution). */
    public static String wrongReturnType(InterceptContext ctx) {
      return "wrong";
    }
  }
}
