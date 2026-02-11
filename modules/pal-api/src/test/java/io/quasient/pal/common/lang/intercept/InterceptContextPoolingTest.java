/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link InterceptContext} pooling and deferred argument array copying.
 *
 * <p>These tests verify the optimized InterceptContext behavior where:
 *
 * <ul>
 *   <li>Argument array copying is deferred until mutation (copy-on-write in {@code setArg()})
 *   <li>Object pooling is supported via a {@code reset()} method
 *   <li>{@link InterceptContext.LocalInterceptMetadata} can be cached and shared across context
 *       instances
 * </ul>
 *
 * @see InterceptContext
 */
public class InterceptContextPoolingTest {

  /** Shared constants for test setup. */
  private static final String CLASS_NAME = "com.example.MyClass";

  /** Shared constants for test setup. */
  private static final String METHOD_NAME = "myMethod";

  /** Shared constants for test setup. */
  private static final String PEER_UUID = "peer-uuid-123";

  /** Shared parameter types for test setup. */
  private static final List<String> PARAM_TYPES = List.of("int", "String", "double");

  /**
   * Verifies that read-only access to arguments does not trigger a defensive copy.
   *
   * <p>When the optimized InterceptContext defers argument array copying, merely reading args via
   * {@code getArgs()} or accessing individual arguments should not allocate a new array internally.
   * The internal args reference should remain the original array passed to the factory method.
   */
  @Test
  public void shouldDeferArgsCopyWhenNoMutation() {
    // Given: InterceptContext created with args [1, "hello", 3.14]
    Object[] originalArgs = new Object[] {1, "hello", 3.14};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, PARAM_TYPES, InterceptType.BEFORE, PEER_UUID, originalArgs);

    // When: getArgs() called (read-only access) — note: getArgs() returns a defensive copy
    // to the caller, but the internal array should still be the original reference
    Object[] externalCopy = ctx.getArgs();
    assertNotNull(externalCopy);
    assertArrayEquals(new Object[] {1, "hello", 3.14}, externalCopy);

    // Then: Internal args array (via getArgsInternal()) is the original reference — no
    // defensive copy was created at construction time
    assertSame(originalArgs, ctx.getArgsInternal());
  }

  /**
   * Verifies that calling {@code setArg()} triggers a copy of the args array on first mutation.
   *
   * <p>The copy-on-write optimization defers array copying until the first call to {@code
   * setArg()}. After the copy, the original array passed to the factory must remain unmodified.
   */
  @Test
  public void shouldCopyArgsOnFirstSetArg() {
    // Given: InterceptContext created with args [1, "hello", 3.14]
    Object[] originalArgs = new Object[] {1, "hello", 3.14};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, PARAM_TYPES, InterceptType.BEFORE, PEER_UUID, originalArgs);

    // When: setArg(0, 42) called
    ctx.setArg(0, 42);

    // Then: Internal args is a copy (original array unchanged), arg[0] == 42
    assertNotSame(originalArgs, ctx.getArgsInternal());
    assertEquals(42, ctx.getArgsInternal()[0]);
    assertEquals(1, originalArgs[0]); // Original unmodified
  }

  /**
   * Verifies that subsequent {@code setArg()} calls after the first mutation do not create
   * additional copies.
   *
   * <p>Once the copy-on-write has been triggered by the first {@code setArg()}, all subsequent
   * mutations should operate on the same copied array without allocating again.
   */
  @Test
  public void shouldNotCopyArgsOnSubsequentSetArg() {
    // Given: InterceptContext where setArg already triggered copy
    Object[] originalArgs = new Object[] {1, "hello", 3.14};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, PARAM_TYPES, InterceptType.BEFORE, PEER_UUID, originalArgs);

    // When: first setArg triggers copy
    ctx.setArg(0, 42);
    Object[] afterFirstCopy = ctx.getArgsInternal();

    // When: setArg(1, "world") called — should NOT create another copy
    ctx.setArg(1, "world");

    // Then: No additional copy created — same internal array reference as after first setArg
    assertSame(afterFirstCopy, ctx.getArgsInternal());
    assertArrayEquals(new Object[] {42, "world", 3.14}, ctx.getArgsInternal());
  }

  /**
   * Verifies that {@code reset()} clears all fields and prepares the context for reuse.
   *
   * <p>After reset, the context object should have all mutable state cleared: args, return value,
   * thrown exception, modification flags, AROUND accessors, proceed state, and metadata references.
   * The object should be ready for reinitialization via a factory or init method.
   */
  @Test
  public void shouldResetCorrectlyForPooling() {
    // Given: InterceptContext previously used (has metadata, args, mutation flags)
    Object[] args = new Object[] {1, "hello", 3.14};
    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, PARAM_TYPES, InterceptType.BEFORE, PEER_UUID, args);

    // Modify the context to set various flags
    ctx.setArg(0, 42);
    assertTrue(ctx.isArgsModified());

    // When: reset() called
    ctx.reset();

    // Then: All fields cleared, object ready for reuse
    assertNull(ctx.getArgsInternal());
    assertFalse(ctx.isArgsModified());
    assertFalse(ctx.isReturnValueModified());
    assertFalse(ctx.isProceedCalled());
    assertNull(ctx.getExceptionToThrow());
    assertNull(ctx.getLocalMetadata());
    assertNull(ctx.getExec());
    assertFalse(ctx.isPooled());

    // Verify the object can be reused (basic structural integrity after reset)
    Object[] newArgs = ctx.getArgs();
    assertNotNull(newArgs);
    assertEquals(0, newArgs.length);
  }

  /**
   * Verifies that {@link InterceptContext.LocalInterceptMetadata} instances can be created once and
   * shared across multiple {@link InterceptContext} instances.
   *
   * <p>Since className, methodName, and paramTypes are the same for all callbacks on the same
   * invocation, the metadata should be created once and reused to avoid repeated allocation and
   * {@code List.copyOf()} overhead.
   */
  @Test
  public void shouldCacheLocalInterceptMetadataAcrossCallbacks() {
    // Given: Same className, methodName, paramTypes for multiple callbacks
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(CLASS_NAME, METHOD_NAME, PARAM_TYPES);

    Object[] args = new Object[] {1, "hello"};

    // When: Create multiple InterceptContext instances sharing that same metadata object
    InterceptContext ctx1 =
        InterceptContext.forLocalBeforePhase(metadata, InterceptType.BEFORE, PEER_UUID, args);
    InterceptContext ctx2 =
        InterceptContext.forLocalBeforePhase(metadata, InterceptType.BEFORE, PEER_UUID, args);
    InterceptContext ctx3 =
        InterceptContext.forLocalBeforePhase(metadata, InterceptType.BEFORE, PEER_UUID, args);

    // Then: All contexts reference the SAME metadata
    assertSame(metadata, ctx1.getLocalMetadata());
    assertSame(metadata, ctx2.getLocalMetadata());
    assertSame(metadata, ctx3.getLocalMetadata());

    // Verify metadata content is correct
    assertEquals(CLASS_NAME, metadata.className());
    assertEquals(METHOD_NAME, metadata.methodName());
    assertEquals(PARAM_TYPES, metadata.paramTypes());
  }

  /**
   * Verifies that the original args array is never corrupted when a callback mutates arguments.
   *
   * <p>This is the critical correctness test for the deferred copy optimization. Even though the
   * args array is not copied eagerly at construction, mutations via {@code setArg()} must trigger a
   * copy-on-write to protect the original array from corruption.
   */
  @Test
  public void shouldNotCorruptOriginalArgsArrayWhenCallbackMutates() {
    // Given: Original args array [1, 2, 3]
    Object[] originalArgs = new Object[] {1, 2, 3};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME,
            METHOD_NAME,
            List.of("int", "int", "int"),
            InterceptType.BEFORE,
            PEER_UUID,
            originalArgs);

    // When: Callback calls setArg(0, 99)
    ctx.setArg(0, 99);

    // Then: Original array still [1, 2, 3]; context args are [99, 2, 3]
    assertArrayEquals(new Object[] {1, 2, 3}, originalArgs);
    assertArrayEquals(new Object[] {99, 2, 3}, ctx.getArgs());

    // Also verify for multiple mutations: setArg(1, 88), setArg(2, 77)
    ctx.setArg(1, 88);
    ctx.setArg(2, 77);

    // Original array must still be {1, 2, 3}
    assertArrayEquals(new Object[] {1, 2, 3}, originalArgs);
    // Context's getArgs() returns {99, 88, 77}
    assertArrayEquals(new Object[] {99, 88, 77}, ctx.getArgs());
  }

  /**
   * Verifies that creating an InterceptContext with null args (e.g., for a no-arg method) is
   * handled gracefully.
   *
   * <p>The deferred copy optimization must handle the null args case without throwing
   * NullPointerException. The behavior should be consistent with the existing contract: {@code
   * getArgs()} returns an empty array for null internal args.
   */
  @Test
  public void shouldHandleNullArgsGracefully() {
    // Given: InterceptContext created with null args (e.g., no-arg method)
    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, List.of(), InterceptType.BEFORE, PEER_UUID, null);

    // When: getArgs() called
    Object[] args = ctx.getArgs();

    // Then: Returns empty array (consistent with existing behavior)
    assertNotNull(args);
    assertEquals(0, args.length);

    // Verify getArgsInternal() returns null (internal representation unchanged)
    assertNull(ctx.getArgsInternal());

    // Verify setArg(0, "anything") throws IllegalStateException
    try {
      ctx.setArg(0, "anything");
      fail("Expected IllegalStateException for no arguments");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("No arguments available"));
    }
  }

  /**
   * Verifies that the pooled factory returns a context marked as pooled.
   *
   * <p>Pooled contexts have the {@code isPooled()} flag set to true, warning callers that the
   * context must not be stored or passed to other threads.
   */
  @Test
  public void shouldMarkPooledContextAsPooled() {
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(CLASS_NAME, METHOD_NAME, PARAM_TYPES);

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhasePooled(
            metadata, InterceptType.BEFORE, PEER_UUID, new Object[] {1, 2});

    assertTrue(ctx.isPooled());
    assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
    assertEquals(InterceptType.BEFORE, ctx.getInterceptType());
    assertEquals(PEER_UUID, ctx.getInterceptedPeerUuid());
    assertSame(metadata, ctx.getLocalMetadata());
  }

  /**
   * Verifies that the pooled factory returns the same instance on the same thread.
   *
   * <p>This confirms that the ThreadLocal pooling mechanism reuses instances rather than allocating
   * new ones.
   */
  @Test
  public void shouldReusePooledInstanceOnSameThread() {
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(CLASS_NAME, METHOD_NAME, PARAM_TYPES);

    InterceptContext ctx1 =
        InterceptContext.forLocalBeforePhasePooled(
            metadata, InterceptType.BEFORE, PEER_UUID, new Object[] {1});

    InterceptContext ctx2 =
        InterceptContext.forLocalBeforePhasePooled(
            metadata, InterceptType.BEFORE, PEER_UUID, new Object[] {2});

    // Same thread-local instance
    assertSame(ctx1, ctx2);

    // But re-initialized with latest args
    assertArrayEquals(new Object[] {2}, ctx2.getArgsInternal());
  }

  /**
   * Verifies that non-pooled factory does NOT mark context as pooled.
   *
   * <p>Fresh allocations via the regular factory methods should not be marked as pooled, ensuring
   * they are safe for async callbacks and storage.
   */
  @Test
  public void shouldNotMarkRegularContextAsPooled() {
    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            CLASS_NAME, METHOD_NAME, PARAM_TYPES, InterceptType.BEFORE, PEER_UUID, new Object[0]);

    assertFalse(ctx.isPooled());
  }

  /**
   * Verifies that LocalInterceptMetadata can be constructed from String[] for optimization.
   *
   * <p>The String[] constructor avoids the overhead of creating an intermediate List when parameter
   * types are already available as an array.
   */
  @Test
  public void shouldSupportStringArrayConstructorForMetadata() {
    String[] paramTypesArray = new String[] {"int", "String"};

    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(CLASS_NAME, METHOD_NAME, paramTypesArray);

    assertEquals(CLASS_NAME, metadata.className());
    assertEquals(METHOD_NAME, metadata.methodName());
    assertEquals(List.of("int", "String"), metadata.paramTypes());

    // Verify defensive copy — modifying original array should not affect metadata
    paramTypesArray[0] = "long";
    assertEquals("int", metadata.paramTypes().get(0));
  }
}
