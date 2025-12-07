/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

import com.quasient.pal.messages.colfer.ExecMessage;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Context object providing access to intercept callback state and operation metadata.
 *
 * <p>An {@code InterceptContext} instance is passed to {@link
 * InterceptCallback#handle(InterceptContext)} and provides access to:
 *
 * <ul>
 *   <li>The {@link ExecMessage} containing operation metadata (class, method, parameter types,
 *       etc.)
 *   <li>The current {@link InterceptPhase} (BEFORE or AFTER)
 *   <li>The {@link InterceptType} of this intercept (BEFORE, AFTER, or AROUND)
 *   <li>Method arguments (readable and modifiable in BEFORE phase)
 *   <li>Return value or thrown exception (readable in AFTER phase)
 *   <li>Helper methods for modifying arguments and return values
 * </ul>
 *
 * <p><b>Limitations:</b>
 *
 * <ul>
 *   <li><b>Argument mutation:</b> Only simple types are supported (primitives, wrapper types,
 *       String, and simple arrays of these). Complex objects will be force-serialized when sent
 *       back.
 *   <li><b>Return value override:</b> Return values are force-serialized (by-value) when sent back
 *       to the intercepted peer. ObjectRef-based remote references are not supported.
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is <b>not</b> thread-safe. Each callback invocation receives
 * its own context instance, but the same callback implementation may be invoked concurrently with
 * different contexts. Do not share context instances across threads.
 *
 * @see InterceptCallback
 * @see InterceptCallbackResponse
 * @see InterceptPhase
 */
public final class InterceptContext {

  /** The execution message containing operation metadata. */
  @Nonnull private final ExecMessage exec;

  /** The current callback phase (BEFORE or AFTER). */
  @Nonnull private final InterceptPhase phase;

  /** The type of intercept (BEFORE, AFTER, or AROUND). */
  @Nonnull private final InterceptType interceptType;

  /** The UUID of the peer being intercepted. */
  @Nonnull private final String interceptedPeerUuid;

  /** The method arguments. Mutable via {@link #setArg(int, Object)}. */
  @Nullable private Object[] args;

  /**
   * The return value from the intercepted method (AFTER phase only).
   *
   * <p>This is {@code null} if:
   *
   * <ul>
   *   <li>The phase is BEFORE
   *   <li>The method is void
   *   <li>The method threw an exception
   *   <li>The method returned null
   * </ul>
   */
  @Nullable private Object returnValue;

  /**
   * Whether the intercepted method has a void return type.
   *
   * <p>This is {@code true} if the method signature declares {@code void} as its return type.
   */
  private final boolean isVoid;

  /**
   * The exception thrown by the intercepted method (AFTER phase only).
   *
   * <p>This is {@code null} if the method completed normally without throwing.
   */
  @Nullable private Throwable thrownException;

  /** Flag indicating whether arguments have been modified. */
  private boolean argsModified = false;

  /** Flag indicating whether the return value has been modified. */
  private boolean returnValueModified = false;

  /** The exception to throw instead of normal execution/return. */
  @Nullable private Throwable exceptionToThrow;

  /**
   * Constructs an {@code InterceptContext} with the specified parameters.
   *
   * @param exec the execution message containing operation metadata
   * @param phase the current callback phase
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (nullable for void methods)
   * @param returnValue the return value (AFTER phase only, nullable)
   * @param isVoid whether the method has a void return type
   * @param thrownException the exception thrown by the method (AFTER phase only, nullable)
   */
  private InterceptContext(
      @Nonnull ExecMessage exec,
      @Nonnull InterceptPhase phase,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args,
      @Nullable Object returnValue,
      boolean isVoid,
      @Nullable Throwable thrownException) {
    this.exec = Objects.requireNonNull(exec, "exec must not be null");
    this.phase = Objects.requireNonNull(phase, "phase must not be null");
    this.interceptType = Objects.requireNonNull(interceptType, "interceptType must not be null");
    this.interceptedPeerUuid =
        Objects.requireNonNull(interceptedPeerUuid, "interceptedPeerUuid must not be null");
    this.args = args;
    this.returnValue = returnValue;
    this.isVoid = isVoid;
    this.thrownException = thrownException;
  }

  /**
   * Creates a new {@code InterceptContext} for the BEFORE phase.
   *
   * @param exec the execution message
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments
   * @return a new BEFORE-phase context
   */
  public static InterceptContext forBeforePhase(
      @Nonnull ExecMessage exec,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    return new InterceptContext(
        exec,
        InterceptPhase.BEFORE,
        interceptType,
        interceptedPeerUuid,
        args != null ? Arrays.copyOf(args, args.length) : null,
        null,
        false,
        null);
  }

  /**
   * Creates a new {@code InterceptContext} for the AFTER phase.
   *
   * @param exec the execution message
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (from BEFORE phase, possibly modified)
   * @param returnValue the return value (null if void or exception thrown)
   * @param isVoid whether the method has a void return type
   * @param thrownException the exception thrown (null if normal completion)
   * @return a new AFTER-phase context
   */
  public static InterceptContext forAfterPhase(
      @Nonnull ExecMessage exec,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args,
      @Nullable Object returnValue,
      boolean isVoid,
      @Nullable Throwable thrownException) {
    return new InterceptContext(
        exec,
        InterceptPhase.AFTER,
        interceptType,
        interceptedPeerUuid,
        args,
        returnValue,
        isVoid,
        thrownException);
  }

  /**
   * Returns the {@link ExecMessage} containing operation metadata.
   *
   * <p>The {@code ExecMessage} provides access to:
   *
   * <ul>
   *   <li>Target class name
   *   <li>Method or field name
   *   <li>Parameter types and values
   *   <li>Caller UUID and timestamp
   *   <li>Other operation-specific metadata
   * </ul>
   *
   * @return the execution message (never null)
   */
  @Nonnull
  public ExecMessage getExec() {
    return exec;
  }

  /**
   * Returns the current callback phase.
   *
   * @return {@link InterceptPhase#BEFORE} or {@link InterceptPhase#AFTER}
   */
  @Nonnull
  public InterceptPhase getPhase() {
    return phase;
  }

  /**
   * Returns the type of this intercept.
   *
   * @return the intercept type (BEFORE, AFTER, or AROUND)
   */
  @Nonnull
  public InterceptType getInterceptType() {
    return interceptType;
  }

  /**
   * Returns the UUID of the peer being intercepted.
   *
   * @return the intercepted peer's UUID (never null)
   */
  @Nonnull
  public String getInterceptedPeerUuid() {
    return interceptedPeerUuid;
  }

  /**
   * Returns a copy of the method arguments.
   *
   * <p>For methods with no parameters, this returns an empty array (not null).
   *
   * <p><b>Note:</b> Arguments are limited to simple types (primitives, wrappers, String, and simple
   * arrays). Complex objects may not be fully deserialized.
   *
   * @return the method arguments array (never null, possibly empty)
   */
  @Nonnull
  public Object[] getArgs() {
    return args != null ? Arrays.copyOf(args, args.length) : new Object[0];
  }

  /**
   * Returns the return value from the intercepted method.
   *
   * <p>This is only meaningful in the {@link InterceptPhase#AFTER} phase.
   *
   * <p>This returns {@code null} if:
   *
   * <ul>
   *   <li>The phase is BEFORE
   *   <li>The method is void ({@link #isVoid()} returns true)
   *   <li>The method threw an exception ({@link #getThrownException()} is non-null)
   *   <li>The method explicitly returned null
   * </ul>
   *
   * @return the return value, or null
   */
  @Nullable
  public Object getReturnValue() {
    return returnValue;
  }

  /**
   * Returns whether the intercepted method has a void return type.
   *
   * @return {@code true} if the method is declared void, {@code false} otherwise
   */
  public boolean isVoid() {
    return isVoid;
  }

  /**
   * Returns the exception thrown by the intercepted method.
   *
   * <p>This is only meaningful in the {@link InterceptPhase#AFTER} phase.
   *
   * <p>If this is non-null, the intercepted method threw an exception instead of returning
   * normally.
   *
   * @return the thrown exception, or null if the method completed normally
   */
  @Nullable
  public Throwable getThrownException() {
    return thrownException;
  }

  /**
   * Sets the value of a specific argument.
   *
   * <p>This is typically used in the {@link InterceptPhase#BEFORE} phase to modify arguments before
   * method execution.
   *
   * <p><b>Limitation:</b> Only simple types are supported (primitives, wrappers, String, and simple
   * arrays). Setting complex objects may result in serialization errors.
   *
   * <p><b>ASYNC Restriction:</b> Argument mutation is not supported for {@link
   * InterceptType#BEFORE_ASYNC} intercepts. ASYNC callbacks are fire-and-forget and cannot modify
   * the arguments because the response is not awaited by the intercepted peer.
   *
   * @param index the zero-based argument index
   * @param value the new argument value
   * @throws IndexOutOfBoundsException if the index is out of range
   * @throws UnsupportedOperationException if the intercept type is BEFORE_ASYNC
   */
  public void setArg(int index, @Nullable Object value) {
    if (interceptType == InterceptType.BEFORE_ASYNC) {
      throw new UnsupportedOperationException(
          "Argument mutation is not supported for BEFORE_ASYNC intercepts. "
              + "ASYNC callbacks are fire-and-forget and cannot modify arguments.");
    }
    if (args == null) {
      throw new IllegalStateException("No arguments available to modify");
    }
    if (index < 0 || index >= args.length) {
      throw new IndexOutOfBoundsException(
          "Argument index " + index + " out of bounds for length " + args.length);
    }
    // Copy-on-write: create a defensive copy on first modification
    if (!argsModified) {
      args = Arrays.copyOf(args, args.length);
      argsModified = true;
    }
    args[index] = value;
  }

  /**
   * Sets the return value to be sent back to the intercepted peer.
   *
   * <p>This is typically used in the {@link InterceptPhase#AFTER} phase to override the method's
   * return value.
   *
   * <p><b>Note:</b> Return values are force-serialized (by-value). ObjectRef-based remote
   * references are not supported.
   *
   * <p><b>ASYNC Restriction:</b> Return value override is not supported for {@link
   * InterceptType#AFTER_ASYNC} intercepts. ASYNC callbacks are fire-and-forget and cannot modify
   * the return value because the response is not awaited by the intercepted peer.
   *
   * @param value the new return value
   * @throws IllegalStateException if the method is void
   * @throws UnsupportedOperationException if the intercept type is AFTER_ASYNC
   */
  public void setReturnValue(@Nullable Object value) {
    if (interceptType == InterceptType.AFTER_ASYNC) {
      throw new UnsupportedOperationException(
          "Return value override is not supported for AFTER_ASYNC intercepts. "
              + "ASYNC callbacks are fire-and-forget and cannot modify the return value.");
    }
    if (isVoid) {
      throw new IllegalStateException("Cannot set return value for void method");
    }
    this.returnValue = value;
    this.returnValueModified = true;
  }

  /**
   * Sets an exception to throw instead of normal execution or return.
   *
   * <p>If set, this exception will be thrown on the intercepted peer instead of:
   *
   * <ul>
   *   <li><b>BEFORE phase:</b> Instead of executing the method
   *   <li><b>AFTER phase:</b> Instead of returning the method's result or original exception
   * </ul>
   *
   * <p><b>ASYNC Restriction:</b> Exception throwing is not supported for {@link
   * InterceptType#BEFORE_ASYNC} or {@link InterceptType#AFTER_ASYNC} intercepts. ASYNC callbacks
   * are fire-and-forget and cannot affect the intercepted peer's execution flow.
   *
   * @param exception the exception to throw
   * @throws UnsupportedOperationException if the intercept type is BEFORE_ASYNC or AFTER_ASYNC
   */
  public void setExceptionToThrow(@Nonnull Throwable exception) {
    if (interceptType == InterceptType.BEFORE_ASYNC || interceptType == InterceptType.AFTER_ASYNC) {
      throw new UnsupportedOperationException(
          "Exception throwing is not supported for "
              + interceptType
              + " intercepts. "
              + "ASYNC callbacks are fire-and-forget and cannot affect execution flow.");
    }
    this.exceptionToThrow = Objects.requireNonNull(exception, "exception cannot be null");
  }

  /**
   * Returns the exception set via {@link #setExceptionToThrow(Throwable)}.
   *
   * <p><b>Internal API:</b> This method is primarily for use by callback dispatchers.
   *
   * @return the exception to throw, or null if no exception was set
   */
  @Nullable
  public Throwable getExceptionToThrow() {
    return exceptionToThrow;
  }

  /**
   * Returns whether any arguments have been modified via {@link #setArg(int, Object)}.
   *
   * <p><b>Internal API:</b> This method is primarily for use by callback dispatchers.
   *
   * @return {@code true} if arguments were modified, {@code false} otherwise
   */
  public boolean isArgsModified() {
    return argsModified;
  }

  /**
   * Returns whether the return value has been modified via {@link #setReturnValue(Object)}.
   *
   * <p><b>Internal API:</b> This method is primarily for use by callback dispatchers.
   *
   * @return {@code true} if return value was modified, {@code false} otherwise
   */
  public boolean isReturnValueModified() {
    return returnValueModified;
  }

  /**
   * Returns the (possibly modified) arguments array for internal use.
   *
   * <p><b>Internal API:</b> This method is primarily for use by callback dispatchers.
   *
   * @return the arguments array
   */
  @Nullable
  public Object[] getArgsInternal() {
    return args;
  }
}
