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
package io.quasient.pal.common.lang.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.util.Arrays;
import java.util.List;
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
 *       etc.) - for remote intercepts
 *   <li>{@link LocalInterceptMetadata} for local intercepts (no ExecMessage overhead)
 *   <li>The current {@link InterceptPhase} (BEFORE or AFTER)
 *   <li>The {@link InterceptType} of this intercept (BEFORE, AFTER, or AROUND)
 *   <li>Method arguments (readable and modifiable in BEFORE phase)
 *   <li>Return value or thrown exception (readable in AFTER phase)
 *   <li>Helper methods for modifying arguments and return values
 * </ul>
 *
 * <p><b>Local vs Remote Intercepts:</b>
 *
 * <ul>
 *   <li><b>Remote intercepts:</b> Created via {@link #forBeforePhase} or {@link #forAfterPhase}
 *       with an ExecMessage. Full metadata available. Arguments are deserialized.
 *   <li><b>Local intercepts:</b> Created via {@link #forLocalBeforePhase} or {@link
 *       #forLocalAfterPhase} without ExecMessage overhead. Arguments are live Java objects. {@link
 *       #getExec()} returns null for local intercepts.
 * </ul>
 *
 * <p><b>Limitations:</b>
 *
 * <ul>
 *   <li><b>Argument mutation:</b> Only simple types are supported for remote intercepts
 *       (primitives, wrapper types, String, and simple arrays). Local intercepts can mutate any
 *       object directly.
 *   <li><b>Return value override:</b> For remote intercepts, return values are force-serialized
 *       (by-value). For local intercepts, return values are passed directly.
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is <b>not</b> thread-safe, but each callback invocation
 * receives its own context instance. The intercept mechanism ensures contexts are never shared
 * across threads, so you can safely mutate context state (arguments, return values, etc.) without
 * synchronization. However, any shared state in your callback implementation (e.g., static fields)
 * follows standard Java concurrency rules.
 *
 * <p><b>Context Isolation:</b>
 *
 * <p>Each callback invocation gets a unique {@code InterceptContext} instance, so there is no risk
 * of concurrent access to the same context. You can safely modify arguments and return values
 * without synchronization.
 *
 * <p><b>Key points:</b>
 *
 * <ul>
 *   <li><b>Context isolation:</b> Each callback invocation gets its own context instance, so you
 *       can safely mutate arguments, return values, etc. without synchronization
 *   <li><b>Shared state:</b> If your callback maintains shared state (e.g., static fields),
 *       standard Java concurrency rules apply - use thread-safe collections or explicit
 *       synchronization (see {@link InterceptCallback} for examples)
 *   <li><b>Don't share contexts:</b> Contexts are single-use and should not be stored in static
 *       fields or passed between threads
 *   <li><b>AROUND phase sequencing:</b> For AROUND intercepts, the same context instance is used
 *       for both BEFORE and AFTER phases sequentially on the same thread (no special
 *       synchronization needed)
 * </ul>
 *
 * <p><b>Example: Context Usage with Shared State</b>
 *
 * <p>This example shows safe usage - context modifications don't need synchronization, but shared
 * state does:
 *
 * <pre>{@code
 * public class SafeCallback implements InterceptCallback {
 *     // Shared state is thread-safe
 *     private final ConcurrentHashMap<String, Integer> counts =
 *         new ConcurrentHashMap<>();
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // Safe: ctx is unique to this invocation, no synchronization needed
 *         Object[] args = ctx.getArgs();
 *         ctx.setArg(0, modifiedValue);
 *
 *         // Safe: only shared state (counts) needs synchronization
 *         String key = String.valueOf(args[0]);
 *         counts.merge(key, 1, Integer::sum);
 *
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example: Common Mistake - Sharing Contexts</b>
 *
 * <p><b>WARNING:</b> This shows a concurrency bug - storing context instances in shared state:
 *
 * <pre>{@code
 * public class UnsafeCallback implements InterceptCallback {
 *     // WRONG: Storing context in static field
 *     private static InterceptContext lastContext;
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // RACE CONDITION: Multiple threads can overwrite lastContext
 *         lastContext = ctx;  // WRONG: Don't share contexts
 *
 *         // WRONG: Using stored context from another thread's invocation
 *         Object[] args = lastContext.getArgs();  // May be from different invocation!
 *
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>AROUND Callback Context Usage:</b>
 *
 * <p>For AROUND intercepts, the same context instance is used across two sequential phases (BEFORE
 * and AFTER) on the same thread - no special synchronization needed:
 *
 * <pre>{@code
 * public class AroundCallback implements InterceptCallback {
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // BEFORE phase: ctx is in BEFORE phase
 *         Object[] args = ctx.getArgs();
 *         ctx.setArg(0, modifiedValue);
 *
 *         // Call proceed() - blocks until method executes
 *         ctx.proceed();
 *
 *         // AFTER phase: same ctx instance, now in AFTER phase
 *         // This is safe because phases execute sequentially, not concurrently
 *         Object returnValue = ctx.getReturnValue();
 *
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * @see InterceptCallback
 * @see InterceptCallbackResponse
 * @see InterceptPhase
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "Internal API - mutable state is intentionally exposed for callback manipulation")
public final class InterceptContext {

  /**
   * Metadata for local intercepts when ExecMessage is not available.
   *
   * <p>Local intercepts (where callback peer == intercepted peer) can avoid the overhead of
   * creating an ExecMessage by using this lightweight metadata container instead.
   *
   * <p><b>Optimization:</b> This metadata can be created once per dispatch and shared across
   * multiple {@link InterceptContext} instances for the same invocation, avoiding repeated
   * allocation and {@code List.copyOf()} overhead.
   */
  public static final class LocalInterceptMetadata {

    /** The fully qualified name of the intercepted class. */
    @Nonnull private final String className;

    /** The name of the intercepted method, constructor, or field. */
    @Nonnull private final String methodName;

    /** The parameter type names as a raw array (avoids List.copyOf() overhead). */
    @Nonnull private final String[] paramTypesArray;

    /** Lazily-initialized immutable list view of parameter types. */
    @Nullable private volatile List<String> paramTypesList;

    /**
     * Creates a new LocalInterceptMetadata from a List of parameter types.
     *
     * @param className the class name (must not be null)
     * @param methodName the method/field name (must not be null)
     * @param paramTypes the parameter types (must not be null, may be empty)
     */
    public LocalInterceptMetadata(
        @Nonnull String className, @Nonnull String methodName, @Nonnull List<String> paramTypes) {
      this.className = Objects.requireNonNull(className, "className must not be null");
      this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
      Objects.requireNonNull(paramTypes, "paramTypes must not be null");
      this.paramTypesArray = paramTypes.toArray(new String[0]);
    }

    /**
     * Creates a new LocalInterceptMetadata from a String array of parameter types.
     *
     * <p>This constructor avoids the {@code List.copyOf()} overhead by accepting the raw array
     * directly. The array is defensively copied to ensure immutability.
     *
     * @param className the class name (must not be null)
     * @param methodName the method/field name (must not be null)
     * @param paramTypes the parameter types array (must not be null, may be empty)
     */
    public LocalInterceptMetadata(
        @Nonnull String className, @Nonnull String methodName, @Nonnull String[] paramTypes) {
      this.className = Objects.requireNonNull(className, "className must not be null");
      this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
      Objects.requireNonNull(paramTypes, "paramTypes must not be null");
      this.paramTypesArray = Arrays.copyOf(paramTypes, paramTypes.length);
    }

    /**
     * Returns the fully qualified name of the intercepted class.
     *
     * @return the class name (never null)
     */
    @Nonnull
    public String className() {
      return className;
    }

    /**
     * Returns the name of the intercepted method, constructor, or field.
     *
     * @return the method name (never null)
     */
    @Nonnull
    public String methodName() {
      return methodName;
    }

    /**
     * Returns the parameter type names as an immutable list.
     *
     * <p>The list is lazily created on first access to avoid allocation if never called.
     *
     * @return the parameter types (never null, may be empty)
     */
    @Nonnull
    public List<String> paramTypes() {
      List<String> result = paramTypesList;
      if (result == null) {
        result = List.of(paramTypesArray);
        paramTypesList = result;
      }
      return result;
    }
  }

  /** ThreadLocal pool for reusable InterceptContext instances. */
  private static final ThreadLocal<InterceptContext> TL_CONTEXT =
      ThreadLocal.withInitial(InterceptContext::new);

  /**
   * The execution message containing operation metadata (null for local intercepts).
   *
   * <p>For remote intercepts, this contains the full ExecMessage with all metadata. For local
   * intercepts, this is null and {@link #localMetadata} is used instead.
   */
  @Nullable private ExecMessage exec;

  /**
   * Metadata for local intercepts (null for remote intercepts).
   *
   * <p>For local intercepts, this provides lightweight access to class/method information without
   * the overhead of ExecMessage creation.
   */
  @Nullable private LocalInterceptMetadata localMetadata;

  /**
   * The current callback phase (BEFORE or AFTER). Mutable for AROUND intercepts after proceed().
   */
  @Nonnull private InterceptPhase phase;

  /** The type of intercept (BEFORE, AFTER, or AROUND). */
  @Nonnull private InterceptType interceptType;

  /** The UUID of the peer being intercepted. */
  @Nonnull private String interceptedPeerUuid;

  /** The method arguments. Mutable via {@link #setArg(int, Object)}. */
  @Nullable private Object[] args;

  // ---- AROUND intercept support fields ----

  /** Socket accessor for remote AROUND proceed() - set by dispatcher before callback invocation. */
  @Nullable private AroundSocketAccessor aroundSocketAccessor;

  /** Local accessor for local AROUND proceed() - set by dispatcher for same-peer intercepts. */
  @Nullable private LocalAroundAccessor localAroundAccessor;

  /** Callback ID for correlating BEFORE/AFTER phases of AROUND intercept. */
  @Nullable private String callbackId;

  /** Timeout in milliseconds for remote AROUND proceed(). */
  private int proceedTimeoutMs;

  /** Flag indicating whether proceed() has been called (AROUND only). */
  private boolean proceedCalled = false;

  /** Whether the method has void return type - mutable for AROUND after proceed(). */
  private boolean isVoidMutable;

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
   * The exception thrown by the intercepted method (AFTER phase only).
   *
   * <p>This is {@code null} if the method completed normally without throwing.
   */
  @Nullable private Throwable thrownException;

  /** Flag indicating whether arguments have been modified. */
  private boolean argsModified = false;

  /**
   * Flag indicating whether the args array has been defensively copied.
   *
   * <p>When false, the internal {@code args} reference points to the original array passed to the
   * factory method. The copy is deferred until the first mutation via {@link #setArg(int, Object)}.
   * This copy-on-write optimization avoids unnecessary array allocation for read-only callbacks.
   */
  private boolean argsCopied = false;

  /** Flag indicating whether the return value has been modified. */
  private boolean returnValueModified = false;

  /** The exception to throw instead of normal execution/return. */
  @Nullable private Throwable exceptionToThrow;

  /**
   * Flag indicating whether this context was obtained from the ThreadLocal pool.
   *
   * <p>Pooled contexts must NOT be stored, passed to other threads, or used beyond the scope of a
   * single synchronous callback invocation. They are recycled after the callback returns.
   */
  private boolean pooled = false;

  /**
   * Constructs an {@code InterceptContext} for remote intercepts (with ExecMessage).
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
    this.localMetadata = null;
    this.phase = Objects.requireNonNull(phase, "phase must not be null");
    this.interceptType = Objects.requireNonNull(interceptType, "interceptType must not be null");
    this.interceptedPeerUuid =
        Objects.requireNonNull(interceptedPeerUuid, "interceptedPeerUuid must not be null");
    this.args = args;
    this.returnValue = returnValue;
    this.isVoidMutable = isVoid;
    this.thrownException = thrownException;
  }

  /**
   * Constructs an {@code InterceptContext} for local intercepts (without ExecMessage).
   *
   * @param localMetadata the lightweight metadata for local intercepts
   * @param phase the current callback phase
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted (same as callback peer)
   * @param args the method arguments (live Java objects, not serialized)
   * @param returnValue the return value (AFTER phase only, nullable)
   * @param isVoid whether the method has a void return type
   * @param thrownException the exception thrown by the method (AFTER phase only, nullable)
   */
  private InterceptContext(
      @Nonnull LocalInterceptMetadata localMetadata,
      @Nonnull InterceptPhase phase,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args,
      @Nullable Object returnValue,
      boolean isVoid,
      @Nullable Throwable thrownException) {
    this.exec = null;
    this.localMetadata = Objects.requireNonNull(localMetadata, "localMetadata must not be null");
    this.phase = Objects.requireNonNull(phase, "phase must not be null");
    this.interceptType = Objects.requireNonNull(interceptType, "interceptType must not be null");
    this.interceptedPeerUuid =
        Objects.requireNonNull(interceptedPeerUuid, "interceptedPeerUuid must not be null");
    this.args = args;
    this.returnValue = returnValue;
    this.isVoidMutable = isVoid;
    this.thrownException = thrownException;
  }

  /**
   * No-arg constructor for ThreadLocal pooling.
   *
   * <p>Creates an uninitialized context that must be initialized via {@link
   * #initForLocalBeforePhase} before use.
   */
  @SuppressWarnings("NullAway")
  private InterceptContext() {
    // Fields will be set by initForLocalBeforePhase()
    this.phase = InterceptPhase.BEFORE;
    this.interceptType = InterceptType.BEFORE;
    this.interceptedPeerUuid = "";
  }

  /**
   * Resets all mutable state, preparing this context for reuse from the ThreadLocal pool.
   *
   * <p>After reset, all fields are cleared:
   *
   * <ul>
   *   <li>exec and localMetadata are null
   *   <li>args, returnValue, thrownException, exceptionToThrow are null
   *   <li>All modification flags (argsModified, argsCopied, returnValueModified) are false
   *   <li>proceedCalled is false
   *   <li>AROUND accessors are null
   *   <li>isVoidMutable is false
   * </ul>
   *
   * <p><b>Internal API:</b> This method is for use by the pooling infrastructure only.
   */
  public void reset() {
    this.exec = null;
    this.localMetadata = null;
    this.phase = InterceptPhase.BEFORE;
    this.interceptType = InterceptType.BEFORE;
    this.interceptedPeerUuid = "";
    this.args = null;
    this.returnValue = null;
    this.isVoidMutable = false;
    this.thrownException = null;
    this.argsModified = false;
    this.argsCopied = false;
    this.returnValueModified = false;
    this.exceptionToThrow = null;
    this.proceedCalled = false;
    this.aroundSocketAccessor = null;
    this.localAroundAccessor = null;
    this.callbackId = null;
    this.proceedTimeoutMs = 0;
    this.pooled = false;
  }

  /**
   * Initializes a pooled context for local BEFORE phase.
   *
   * <p><b>Internal API:</b> Used by {@link #forLocalBeforePhasePooled} to configure a recycled
   * context instance.
   *
   * @param metadata the pre-built metadata
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (NOT copied — deferred until setArg())
   */
  private void initForLocalBeforePhase(
      @Nonnull LocalInterceptMetadata metadata,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    this.exec = null;
    this.localMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
    this.phase = InterceptPhase.BEFORE;
    this.interceptType = Objects.requireNonNull(interceptType, "interceptType must not be null");
    this.interceptedPeerUuid =
        Objects.requireNonNull(interceptedPeerUuid, "interceptedPeerUuid must not be null");
    this.args = args;
    this.returnValue = null;
    this.isVoidMutable = false;
    this.thrownException = null;
    this.argsModified = false;
    this.argsCopied = false;
    this.returnValueModified = false;
    this.exceptionToThrow = null;
    this.proceedCalled = false;
    this.aroundSocketAccessor = null;
    this.localAroundAccessor = null;
    this.callbackId = null;
    this.proceedTimeoutMs = 0;
    this.pooled = true;
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

  // ---- Local intercept factory methods (no ExecMessage overhead) ----

  /**
   * Creates a new {@code InterceptContext} for the BEFORE phase of a local intercept.
   *
   * <p>Local intercepts are handled within the same JVM (callback peer == intercepted peer), so
   * there is no need to create an ExecMessage. Arguments are live Java objects, not serialized.
   *
   * @param className the fully qualified name of the intercepted class
   * @param methodName the name of the intercepted method, constructor, or field
   * @param paramTypes the parameter type names (may be empty for fields or no-arg methods)
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @return a new BEFORE-phase context for local intercept
   */
  public static InterceptContext forLocalBeforePhase(
      @Nonnull String className,
      @Nonnull String methodName,
      @Nonnull List<String> paramTypes,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    LocalInterceptMetadata metadata = new LocalInterceptMetadata(className, methodName, paramTypes);
    return forLocalBeforePhase(metadata, interceptType, interceptedPeerUuid, args);
  }

  /**
   * Creates a new {@code InterceptContext} for the BEFORE phase of a local intercept, reusing
   * pre-built metadata.
   *
   * <p>This overload accepts a pre-built {@link LocalInterceptMetadata} to avoid repeated metadata
   * allocation when dispatching multiple callbacks for the same invocation.
   *
   * <p><b>Optimization:</b> The args array is NOT eagerly copied. Instead, copy is deferred until
   * the first {@link #setArg(int, Object)} call (copy-on-write). This means zero-cost for read-only
   * callbacks.
   *
   * @param metadata the pre-built metadata (shared across callbacks)
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @return a new BEFORE-phase context for local intercept
   */
  public static InterceptContext forLocalBeforePhase(
      @Nonnull LocalInterceptMetadata metadata,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    // Deferred copy: args reference is stored directly, copy happens on first setArg()
    return new InterceptContext(
        metadata,
        InterceptPhase.BEFORE,
        interceptType,
        interceptedPeerUuid,
        args,
        null,
        false,
        null);
  }

  /**
   * Returns a pooled {@code InterceptContext} for the BEFORE phase of a local intercept.
   *
   * <p>This method reuses a thread-local context instance, avoiding allocation entirely for
   * synchronous callbacks. The pooled context is initialized with the given parameters and marked
   * as pooled ({@link #isPooled()} returns true).
   *
   * <p><b>CRITICAL SAFETY CONSTRAINT:</b> The pooled context is ONLY safe for local BEFORE/AFTER
   * callbacks where the callback is invoked synchronously and the context is not stored. For ASYNC
   * callbacks (BEFORE_ASYNC, AFTER_ASYNC), a fresh allocation via {@link #forLocalBeforePhase} is
   * required because the callback may execute on a different thread after the context is recycled.
   *
   * <p><b>Lifecycle:</b>
   *
   * <ol>
   *   <li>Caller obtains the pooled context via this method
   *   <li>Caller invokes the callback synchronously
   *   <li>Caller reads any mutations from the context
   *   <li>Context is automatically available for reuse on the next call (same thread)
   * </ol>
   *
   * @param metadata the pre-built metadata (shared across callbacks)
   * @param interceptType the type of intercept (must be BEFORE, not BEFORE_ASYNC)
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (NOT copied — deferred until setArg())
   * @return the pooled BEFORE-phase context (same thread-local instance each time)
   */
  public static InterceptContext forLocalBeforePhasePooled(
      @Nonnull LocalInterceptMetadata metadata,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    InterceptContext ctx = TL_CONTEXT.get();
    ctx.initForLocalBeforePhase(metadata, interceptType, interceptedPeerUuid, args);
    return ctx;
  }

  /**
   * Creates a new {@code InterceptContext} for the AFTER phase of a local intercept.
   *
   * <p>Local intercepts are handled within the same JVM (callback peer == intercepted peer), so
   * there is no need to create an ExecMessage. Arguments and return values are live Java objects.
   *
   * @param className the fully qualified name of the intercepted class
   * @param methodName the name of the intercepted method, constructor, or field
   * @param paramTypes the parameter type names (may be empty for fields or no-arg methods)
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @param returnValue the return value (null if void or exception thrown)
   * @param isVoid whether the method has a void return type
   * @param thrownException the exception thrown (null if normal completion)
   * @return a new AFTER-phase context for local intercept
   */
  public static InterceptContext forLocalAfterPhase(
      @Nonnull String className,
      @Nonnull String methodName,
      @Nonnull List<String> paramTypes,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args,
      @Nullable Object returnValue,
      boolean isVoid,
      @Nullable Throwable thrownException) {
    LocalInterceptMetadata metadata = new LocalInterceptMetadata(className, methodName, paramTypes);
    return forLocalAfterPhase(
        metadata, interceptType, interceptedPeerUuid, args, returnValue, isVoid, thrownException);
  }

  /**
   * Creates a new {@code InterceptContext} for the AFTER phase of a local intercept, reusing
   * pre-built metadata.
   *
   * <p>This overload accepts a pre-built {@link LocalInterceptMetadata} to avoid repeated metadata
   * allocation when dispatching multiple callbacks for the same invocation.
   *
   * @param metadata the pre-built metadata (shared across callbacks)
   * @param interceptType the type of intercept
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @param returnValue the return value (null if void or exception thrown)
   * @param isVoid whether the method has a void return type
   * @param thrownException the exception thrown (null if normal completion)
   * @return a new AFTER-phase context for local intercept
   */
  public static InterceptContext forLocalAfterPhase(
      @Nonnull LocalInterceptMetadata metadata,
      @Nonnull InterceptType interceptType,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args,
      @Nullable Object returnValue,
      boolean isVoid,
      @Nullable Throwable thrownException) {
    return new InterceptContext(
        metadata,
        InterceptPhase.AFTER,
        interceptType,
        interceptedPeerUuid,
        args,
        returnValue,
        isVoid,
        thrownException);
  }

  /**
   * Creates a new {@code InterceptContext} for a local AROUND intercept.
   *
   * <p>Local AROUND intercepts are handled within the same JVM and use direct method invocation
   * instead of socket communication. The context starts in BEFORE phase; when {@link #proceed()} is
   * called, the {@code LocalAroundAccessor} directly invokes the method.
   *
   * <p><b>Usage pattern:</b>
   *
   * <pre>{@code
   * InterceptContext ctx = InterceptContext.forLocalAroundPhase(
   *     className, methodName, paramTypes, peerUuid, args);
   * ctx.setLocalAroundAccessor(accessor);
   * InterceptCallbackResponse response = callback.handle(ctx);
   * }</pre>
   *
   * @param className the fully qualified name of the intercepted class
   * @param methodName the name of the intercepted method, constructor, or field
   * @param paramTypes the parameter type names (may be empty for fields or no-arg methods)
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @return a new context for local AROUND intercept (starts in BEFORE phase)
   */
  public static InterceptContext forLocalAroundPhase(
      @Nonnull String className,
      @Nonnull String methodName,
      @Nonnull List<String> paramTypes,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    LocalInterceptMetadata metadata = new LocalInterceptMetadata(className, methodName, paramTypes);
    return forLocalAroundPhase(metadata, interceptedPeerUuid, args);
  }

  /**
   * Creates a new {@code InterceptContext} for a local AROUND intercept, reusing pre-built
   * metadata.
   *
   * <p>This overload accepts a pre-built {@link LocalInterceptMetadata} to avoid repeated metadata
   * allocation when dispatching multiple callbacks for the same invocation.
   *
   * <p><b>Optimization:</b> The args array is NOT eagerly copied. Instead, copy is deferred until
   * the first {@link #setArg(int, Object)} call (copy-on-write).
   *
   * @param metadata the pre-built metadata (shared across callbacks)
   * @param interceptedPeerUuid the UUID of the peer being intercepted
   * @param args the method arguments (live Java objects)
   * @return a new context for local AROUND intercept (starts in BEFORE phase)
   */
  public static InterceptContext forLocalAroundPhase(
      @Nonnull LocalInterceptMetadata metadata,
      @Nonnull String interceptedPeerUuid,
      @Nullable Object[] args) {
    // Deferred copy: args reference is stored directly, copy happens on first setArg()
    return new InterceptContext(
        metadata,
        InterceptPhase.BEFORE,
        InterceptType.AROUND,
        interceptedPeerUuid,
        args,
        null,
        false,
        null);
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
   * <p><b>Note:</b> For local intercepts, this returns {@code null}. Use {@link
   * #isLocalIntercept()} to check, or use {@link #getLocalMetadata()} for local intercept metadata.
   *
   * @return the execution message, or null for local intercepts
   */
  @Nullable
  public ExecMessage getExec() {
    return exec;
  }

  /**
   * Returns whether this is a local intercept (callback handled in same JVM).
   *
   * <p>Local intercepts do not have an ExecMessage and instead use lightweight {@link
   * LocalInterceptMetadata}.
   *
   * @return {@code true} if this is a local intercept, {@code false} for remote intercepts
   */
  public boolean isLocalIntercept() {
    return localMetadata != null;
  }

  /**
   * Returns the metadata for local intercepts.
   *
   * <p>This is only available for local intercepts (where {@link #isLocalIntercept()} returns
   * true). For remote intercepts, use {@link #getExec()} instead.
   *
   * @return the local intercept metadata, or null for remote intercepts
   */
  @Nullable
  public LocalInterceptMetadata getLocalMetadata() {
    return localMetadata;
  }

  /**
   * Returns whether this context was obtained from the ThreadLocal pool.
   *
   * <p>Pooled contexts must NOT be stored, passed to other threads, or used beyond the scope of a
   * single synchronous callback invocation. Attempting to store a pooled context (e.g., in a static
   * field or collection) will result in undefined behavior when the context is recycled.
   *
   * @return {@code true} if this context is pooled, {@code false} for freshly allocated contexts
   */
  public boolean isPooled() {
    return pooled;
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
   * <p>This is only available in the {@link InterceptPhase#AFTER} phase. For AROUND intercepts,
   * this becomes available after {@link #proceed()} is called.
   *
   * <p>This returns {@code null} if:
   *
   * <ul>
   *   <li>The method is void ({@link #isVoid()} returns true)
   *   <li>The method threw an exception ({@link #getThrownException()} is non-null)
   *   <li>The method explicitly returned null
   * </ul>
   *
   * @return the return value, or null
   * @throws InterceptTypeNotSupportedException if intercept type is BEFORE or BEFORE_ASYNC (return
   *     value never available)
   * @throws InterceptPhaseViolationException if AROUND intercept before proceed() (return value not
   *     yet available)
   */
  @Nullable
  public Object getReturnValue() {
    // BEFORE and BEFORE_ASYNC intercepts never have access to return value
    if (interceptType == InterceptType.BEFORE || interceptType == InterceptType.BEFORE_ASYNC) {
      throw new InterceptTypeNotSupportedException("getReturnValue()", interceptType);
    }
    // AROUND intercept before proceed() - supported but not in this phase
    if (phase == InterceptPhase.BEFORE) {
      throw new InterceptPhaseViolationException(
          "getReturnValue()", InterceptPhase.BEFORE, InterceptPhase.AFTER);
    }
    return returnValue;
  }

  /**
   * Returns whether the intercepted method has a void return type.
   *
   * @return {@code true} if the method is declared void, {@code false} otherwise
   */
  public boolean isVoid() {
    return isVoidMutable;
  }

  /**
   * Returns the exception thrown by the intercepted method.
   *
   * <p>This is only available in the {@link InterceptPhase#AFTER} phase. For AROUND intercepts,
   * this becomes available after {@link #proceed()} is called.
   *
   * <p>If this is non-null, the intercepted method threw an exception instead of returning
   * normally.
   *
   * @return the thrown exception, or null if the method completed normally
   * @throws InterceptTypeNotSupportedException if intercept type is BEFORE or BEFORE_ASYNC (thrown
   *     exception never available)
   * @throws InterceptPhaseViolationException if AROUND intercept before proceed() (thrown exception
   *     not yet available)
   */
  @Nullable
  public Throwable getThrownException() {
    // BEFORE and BEFORE_ASYNC intercepts never have access to thrown exception
    if (interceptType == InterceptType.BEFORE || interceptType == InterceptType.BEFORE_ASYNC) {
      throw new InterceptTypeNotSupportedException("getThrownException()", interceptType);
    }
    // AROUND intercept before proceed() - supported but not in this phase
    if (phase == InterceptPhase.BEFORE) {
      throw new InterceptPhaseViolationException(
          "getThrownException()", InterceptPhase.BEFORE, InterceptPhase.AFTER);
    }
    return thrownException;
  }

  /**
   * Sets the value of a specific argument.
   *
   * <p>This is only available in the {@link InterceptPhase#BEFORE} phase to modify arguments before
   * method execution. For AROUND intercepts, this must be called before {@link #proceed()}.
   *
   * <p><b>Limitation:</b> Only simple types are supported (primitives, wrappers, String, and simple
   * arrays). Setting complex objects may result in serialization errors.
   *
   * <p><b>Type restrictions:</b>
   *
   * <ul>
   *   <li>AFTER/AFTER_ASYNC intercepts cannot mutate arguments (execution already happened)
   *   <li>BEFORE_ASYNC intercepts cannot mutate arguments (fire-and-forget, response not awaited)
   * </ul>
   *
   * @param index the zero-based argument index
   * @param value the new argument value
   * @throws IndexOutOfBoundsException if the index is out of range
   * @throws InterceptTypeNotSupportedException if the intercept type is AFTER, BEFORE_ASYNC, or
   *     AFTER_ASYNC
   * @throws InterceptPhaseViolationException if called in AFTER phase (for AROUND after proceed())
   */
  public void setArg(int index, @Nullable Object value) {
    // Check for unsupported intercept types
    if (interceptType == InterceptType.AFTER || interceptType == InterceptType.AFTER_ASYNC) {
      throw new InterceptTypeNotSupportedException("setArg()", interceptType);
    }
    if (interceptType == InterceptType.BEFORE_ASYNC) {
      throw new InterceptTypeNotSupportedException("setArg()", interceptType);
    }
    // Check phase for AROUND intercepts (after proceed(), we're in AFTER phase)
    if (phase == InterceptPhase.AFTER) {
      throw new InterceptPhaseViolationException(
          "setArg()", InterceptPhase.AFTER, InterceptPhase.BEFORE);
    }
    if (args == null) {
      throw new IllegalStateException("No arguments available to modify");
    }
    if (index < 0 || index >= args.length) {
      throw new IndexOutOfBoundsException(
          "Argument index " + index + " out of bounds for length " + args.length);
    }
    // Copy-on-write: create a defensive copy on first modification
    // This handles both the deferred-copy case (argsCopied=false, args is original reference)
    // and the legacy case where args was already copied at construction time
    if (!argsCopied) {
      args = Arrays.copyOf(args, args.length);
      argsCopied = true;
    }
    argsModified = true;
    args[index] = value;
  }

  /**
   * Sets the return value to be sent back to the intercepted peer.
   *
   * <p>This is available for AFTER and AROUND intercepts. For AROUND intercepts, this can be called
   * either before {@link #proceed()} (to skip execution and return a custom value) or after
   * proceed() (to override the method's return value).
   *
   * <p><b>Note:</b> Return values are force-serialized (by-value). ObjectRef-based remote
   * references are not supported.
   *
   * <p><b>Type restrictions:</b>
   *
   * <ul>
   *   <li>BEFORE intercepts cannot override return value (use AROUND with skipProceed for that)
   *   <li>ASYNC intercepts cannot override return value (fire-and-forget, response not awaited)
   * </ul>
   *
   * @param value the new return value
   * @throws InterceptApiMisuseException if the method is void
   * @throws InterceptTypeNotSupportedException if the intercept type is BEFORE, BEFORE_ASYNC, or
   *     AFTER_ASYNC
   */
  public void setReturnValue(@Nullable Object value) {
    // Check for unsupported intercept types
    if (interceptType == InterceptType.BEFORE) {
      throw new InterceptTypeNotSupportedException("setReturnValue()", interceptType);
    }
    if (interceptType == InterceptType.BEFORE_ASYNC || interceptType == InterceptType.AFTER_ASYNC) {
      throw new InterceptTypeNotSupportedException("setReturnValue()", interceptType);
    }
    if (isVoidMutable) {
      throw new InterceptApiMisuseException(
          "Cannot set return value for void method", "setReturnValue()", interceptType, phase);
    }
    this.returnValue = value;
    this.returnValueModified = true;
  }

  /**
   * Sets an exception to throw instead of normal execution or return.
   *
   * <p>If set, this exception will be thrown on the intercepted peer instead of executing the
   * method (for BEFORE intercepts) or returning the method's result (for AFTER/AROUND intercepts).
   *
   * <p><b>Use cases:</b>
   *
   * <ul>
   *   <li>BEFORE: Security checks, validation, rate limiting - reject before execution
   *   <li>AFTER: Transform or replace exceptions thrown by the method
   *   <li>AROUND: Either of the above, depending on phase
   * </ul>
   *
   * <p><b>Type restrictions:</b>
   *
   * <ul>
   *   <li>ASYNC intercepts cannot throw exceptions (fire-and-forget, cannot affect execution flow)
   * </ul>
   *
   * @param exception the exception to throw
   * @throws InterceptTypeNotSupportedException if the intercept type is BEFORE_ASYNC or AFTER_ASYNC
   */
  public void setExceptionToThrow(@Nonnull Throwable exception) {
    if (interceptType == InterceptType.BEFORE_ASYNC || interceptType == InterceptType.AFTER_ASYNC) {
      throw new InterceptTypeNotSupportedException("setExceptionToThrow()", interceptType);
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
   * Returns the return value without phase validation, for internal use.
   *
   * <p><b>Internal API:</b> This method is for use by callback dispatchers when building responses.
   * Unlike {@link #getReturnValue()}, this method does not throw if called in BEFORE phase. This is
   * necessary for AROUND intercepts that skip execution (don't call proceed()) but still need to
   * return a custom value.
   *
   * @return the return value (may be null)
   */
  @Nullable
  public Object getReturnValueInternal() {
    return returnValue;
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

  // ---- AROUND intercept proceed() support ----

  /**
   * Proceeds with the intercepted method execution (AROUND intercepts only).
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Sends the BEFORE phase response (with any argument mutations)
   *   <li>Blocks until the method executes and AFTER message arrives
   *   <li>Updates this context with return value / thrown exception
   *   <li>Returns the execution result
   * </ol>
   *
   * <p>After {@code proceed()} returns, you can:
   *
   * <ul>
   *   <li>Access {@link #getReturnValue()} for the method's return value
   *   <li>Access {@link #getThrownException()} if the method threw
   *   <li>Override via {@link #setReturnValue(Object)} before returning
   * </ul>
   *
   * <p><b>Note:</b> This method can only be called once per callback invocation.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * public InterceptCallbackResponse handle(InterceptContext ctx) {
   *     // Pre-proceed logic (BEFORE phase)
   *     // Extract key from the intercepted method's first argument
   *     Object key = ctx.getArgs()[0];
   *     Object cached = cache.get(key);
   *     if (cached != null) {
   *         ctx.setReturnValue(cached);
   *         return InterceptCallbackResponse.skipProceed();
   *     }
   *
   *     // Execute the method
   *     ProceedResult result = ctx.proceed();
   *
   *     // Post-proceed logic (AFTER phase)
   *     if (!result.hasException()) {
   *         cache.put(key, result.getReturnValue());
   *     }
   *
   *     return new InterceptCallbackResponse();
   * }
   * }</pre>
   *
   * @return the result of method execution
   * @throws InterceptTypeNotSupportedException if intercept type is not AROUND (proceed only valid
   *     for AROUND)
   * @throws InterceptApiMisuseException if proceed() was already called (can only be called once)
   * @throws AroundTimeoutException if timeout exceeded waiting for method execution
   */
  public ProceedResult proceed() {
    if (interceptType != InterceptType.AROUND) {
      throw new InterceptTypeNotSupportedException("proceed()", interceptType);
    }
    if (proceedCalled) {
      throw new InterceptApiMisuseException(
          "proceed() can only be called once per callback invocation",
          "proceed()",
          interceptType,
          phase);
    }
    if (aroundSocketAccessor == null && localAroundAccessor == null) {
      throw new IllegalStateException("No AROUND accessor set - internal error");
    }

    proceedCalled = true;

    AfterPhaseData afterData;

    if (localAroundAccessor != null) {
      // Local AROUND: direct method invocation (no serialization, no network)
      afterData = localAroundAccessor.invokeMethod(args);
    } else {
      // Remote AROUND: send BEFORE response, receive AFTER via socket
      InterceptCallbackResponseMessage beforeResponse = buildBeforeResponse();
      afterData = aroundSocketAccessor.sendBeforeAndReceiveAfter(beforeResponse, proceedTimeoutMs);
    }

    // Update context with AFTER data
    this.returnValue = afterData.returnValue();
    this.thrownException = afterData.thrownException();
    this.isVoidMutable = afterData.isVoid();
    this.phase = InterceptPhase.AFTER;
    this.returnValueModified = false; // Reset for AFTER phase modifications

    return new ProceedResult(afterData.returnValue(), afterData.thrownException());
  }

  /**
   * Returns whether {@link #proceed()} was called.
   *
   * <p><b>Internal API:</b> Used by dispatchers to determine response type.
   *
   * @return {@code true} if proceed() was called, {@code false} otherwise
   */
  public boolean isProceedCalled() {
    return proceedCalled;
  }

  /**
   * Sets the socket accessor for AROUND proceed().
   *
   * <p><b>Internal API:</b> Called by dispatcher before invoking callback.
   *
   * @param accessor the socket accessor for send/receive operations
   * @param callbackId the callback ID for correlating BEFORE/AFTER phases
   * @param proceedTimeoutMs timeout in milliseconds for proceed() (0 = infinite)
   */
  public void setAroundAccessor(
      @Nonnull AroundSocketAccessor accessor, @Nonnull String callbackId, int proceedTimeoutMs) {
    this.aroundSocketAccessor = Objects.requireNonNull(accessor, "accessor must not be null");
    this.callbackId = Objects.requireNonNull(callbackId, "callbackId must not be null");
    this.proceedTimeoutMs = proceedTimeoutMs;
  }

  /**
   * Sets the local accessor for local AROUND proceed().
   *
   * <p><b>Internal API:</b> Called by dispatcher before invoking callback for same-peer intercepts.
   *
   * <p>Local AROUND intercepts use direct method invocation instead of socket communication,
   * eliminating serialization overhead and network latency.
   *
   * @param accessor the local accessor for direct method invocation
   */
  public void setLocalAroundAccessor(@Nonnull LocalAroundAccessor accessor) {
    this.localAroundAccessor = Objects.requireNonNull(accessor, "accessor must not be null");
  }

  /**
   * Builds the BEFORE phase response message for AROUND proceed().
   *
   * @return the BEFORE response message
   */
  private InterceptCallbackResponseMessage buildBeforeResponse() {
    InterceptCallbackResponseMessage response = new InterceptCallbackResponseMessage();
    response.setCallbackId(callbackId);
    response.setPhase(InterceptPhase.BEFORE.toByte());
    response.setShouldProceed(true);

    if (argsModified && args != null) {
      response.setMutatedArgs(Wrapper.wrapArgsForceByValue(args));
    }

    return response;
  }
}
