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
package io.quasient.pal.weave;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.runtime.DispatchForwarder;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aspect which forwards all method, constructor and field access join points to {@link
 * DispatchForwarder} for handling.
 *
 * <p>The aspect is written in @AspectJ style and is intended to be used together with {@code
 * FullQuantizeSoftening.aj}, which declares soft exceptions for the {@code DispatchForwarder}
 * calls.
 */
@SuppressWarnings("unused")
@Aspect
public class FullQuantizeAspect {

  /** Logger instance used for logging internal events and error messages. */
  private static final Logger logger = LoggerFactory.getLogger(FullQuantizeAspect.class);

  /**
   * Records a guard key identifying the innermost call-site advice currently active on the thread.
   *
   * <p>Each call-site advice saves the previous value, stores its own guard key (see {@link
   * #computeMethodGuardKey(JoinPoint)}), dispatches, and restores the previous value in a {@code
   * finally} block (prev/restore pattern). Nested woven-to-woven calls are handled naturally: the
   * current thread-local holds the key of the deepest active call-site at all times.
   *
   * <p>Execution-site advice uses this to avoid double-dispatch. If the thread-local's key matches
   * the execution-site's own key (computed the same way from the execution join point), the
   * call-site already dispatched for this invocation and the advice simply proceeds. If the keys
   * differ (or the thread-local is {@code null}), the call-site did not dispatch for this
   * invocation — the caller is unwoven (reflection, method references, {@code invokedynamic}, JNI,
   * JDK) — so the execution-site advice must dispatch itself.
   *
   * <p>The key must include the runtime receiver class rather than the static call-site type so
   * that call-site and execution-site keys agree in the presence of virtual dispatch, interface
   * dispatch, and super calls. See {@link #computeMethodGuardKey(JoinPoint)} for the format.
   *
   * <p>The PAL runtime can set the slot to {@link #RUNTIME_INVOKE_SENTINEL} around its own
   * reflective invocation of an incoming message target (e.g. {@code
   * MethodDispatcher.invokeIncoming} calling {@link java.lang.reflect.Method#invoke}). In that
   * state the execution-site advice on the target body must not dispatch, because the runtime owns
   * the recording decision for the incoming call (gated by flags such as {@code
   * --wal-incoming-rpc}).
   */
  public static final ThreadLocal<String> TL_CURRENT_CALL_SIG = new ThreadLocal<>();

  /**
   * Sentinel value the PAL runtime places in {@link #TL_CURRENT_CALL_SIG} while it reflectively
   * invokes an incoming-message target. It is compared by <strong>reference identity</strong>
   * ({@code ==}), not by {@link String#equals(Object)} — this is load-bearing: the identity check
   * guarantees no user-supplied join-point key can ever collide with the sentinel, regardless of
   * its content. Execution-site advice treats this state as "a woven caller already claimed this
   * invocation" and proceeds without dispatching.
   *
   * <p><strong>Do not change the {@code ==} comparison in {@link
   * #callSiteAlreadyDispatched(JoinPoint)} to {@code .equals(...)}.</strong> A value-equal but
   * non-identical string constructed from user input (or deserialised from a wire format) could
   * otherwise forge the sentinel and cause exec-site advice to silently skip dispatch on a call the
   * runtime did not claim.
   */
  public static final String RUNTIME_INVOKE_SENTINEL = "<pal-runtime-reflective-invoke>";

  /**
   * Cache of canonical guard keys indexed by runtime receiver class and the advice's static join
   * point.
   *
   * <p>On the hot path, {@link #computeMethodGuardKey(JoinPoint)} looks up the key here before
   * allocating anything. The outer {@link ClassValue} partitions per runtime class (JVM-intrinsic
   * fast path, no synchronization). The inner {@link ConcurrentHashMap} maps {@link
   * JoinPoint.StaticPart} — an AspectJ-interned singleton per woven call or execution location — to
   * the canonical key string. Entries are GC-eligible when their class becomes unreachable.
   */
  private static final ClassValue<ConcurrentHashMap<JoinPoint.StaticPart, String>> GUARD_KEY_CACHE =
      new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<JoinPoint.StaticPart, String> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  /**
   * Intern map for guard keys so that call-site and execution-site advice — which use different
   * {@link JoinPoint.StaticPart} objects but resolve to the same (runtime class, method-descriptor)
   * pair — observe the <strong>same</strong> {@link String} reference.
   *
   * <p>This is what makes {@code ==} work as the fast path in {@link
   * #callSiteAlreadyDispatched(JoinPoint)} for the common woven-to-woven direct-call case,
   * bypassing the character walk that {@link String#equals(Object)} would otherwise perform.
   */
  private static final ConcurrentHashMap<String, String> INTERNED_GUARD_KEYS =
      new ConcurrentHashMap<>();

  /**
   * Enters a PAL-runtime reflective invocation scope: saves the current value of {@link
   * #TL_CURRENT_CALL_SIG} and installs {@link #RUNTIME_INVOKE_SENTINEL}. The caller must pass the
   * returned value to {@link #endRuntimeInvoke(String)} in a {@code finally} block to restore the
   * previous state.
   *
   * @return the previous thread-local value (to restore in a matching {@code finally} block)
   */
  public static String beginRuntimeInvoke() {
    String prev = TL_CURRENT_CALL_SIG.get();
    TL_CURRENT_CALL_SIG.set(RUNTIME_INVOKE_SENTINEL);
    return prev;
  }

  /**
   * Exits a PAL-runtime reflective invocation scope by restoring the value captured by {@link
   * #beginRuntimeInvoke()}.
   *
   * @param prev the value previously returned by {@link #beginRuntimeInvoke()}
   */
  public static void endRuntimeInvoke(String prev) {
    TL_CURRENT_CALL_SIG.set(prev);
  }

  /* POINTCUT DEFINITIONS */

  /**
   * Matches all classes to be woven, excluding this aspect itself, core framework classes and enum
   * types.
   */
  @Pointcut(
      "!within(io.quasient.pal.weave..*) && "
          + "!within(io.quasient.pal.core..*) && "
          + "!within(is(EnumType))")
  private void allClasses() {}

  /** Matches calls to non-static void methods in the selected classes. */
  @Pointcut("allClasses() && call(!static void *(..))")
  private void voidInstanceMethods() {}

  /** Matches calls to static void methods in the selected classes. */
  @Pointcut("allClasses() && call(static void *(..))")
  private void voidClassMethods() {}

  /** Matches calls to non-static methods that return a value in the selected classes. */
  @Pointcut("allClasses() && call(!static !void *(..))")
  private void nonVoidInstanceMethods() {}

  /** Matches calls to static methods that return a value in the selected classes. */
  @Pointcut("allClasses() && call(static !void *(..))")
  private void nonVoidClassMethods() {}

  /** Matches constructor calls in the selected classes. */
  @Pointcut("allClasses() && call(new(..))")
  private void constructors() {}

  /**
   * Matches executions of non-static void methods in the selected classes.
   *
   * <p>Supplements {@link #voidInstanceMethods()} by firing on the callee side, capturing
   * invocations originating from unwoven callers (e.g. reflection, method references, JNI, or
   * framework callbacks).
   */
  @Pointcut("allClasses() && execution(!static void *(..))")
  private void voidInstanceMethodsExec() {}

  /**
   * Matches executions of static void methods in the selected classes.
   *
   * <p>Supplements {@link #voidClassMethods()} by firing on the callee side, capturing invocations
   * originating from unwoven callers (e.g. reflection, method references, JNI, or framework
   * callbacks).
   */
  @Pointcut("allClasses() && execution(static void *(..))")
  private void voidClassMethodsExec() {}

  /**
   * Matches executions of non-static methods returning a value in the selected classes.
   *
   * <p>Supplements {@link #nonVoidInstanceMethods()} by firing on the callee side, capturing
   * invocations originating from unwoven callers (e.g. reflection, method references, JNI, or
   * framework callbacks).
   */
  @Pointcut("allClasses() && execution(!static !void *(..))")
  private void nonVoidInstanceMethodsExec() {}

  /**
   * Matches executions of static methods returning a value in the selected classes.
   *
   * <p>Supplements {@link #nonVoidClassMethods()} by firing on the callee side, capturing
   * invocations originating from unwoven callers (e.g. reflection, method references, JNI, or
   * framework callbacks).
   */
  @Pointcut("allClasses() && execution(static !void *(..))")
  private void nonVoidClassMethodsExec() {}

  /** Matches get access to static fields in the selected classes. */
  @Pointcut("allClasses() && get(static * *)")
  private void staticGetfields() {}

  /** Matches get access to non-static fields in the selected classes. */
  @Pointcut("allClasses() && get(!static * *)")
  private void nonStaticGetfields() {}

  /** Matches set access to static, non-final fields in the selected classes. */
  @Pointcut("allClasses() && set(static !final * *)")
  private void staticPutfields() {}

  /** Matches set access to non-static, non-final fields in the selected classes. */
  @Pointcut("allClasses() && set(!static !final * *)")
  private void nonStaticPutfields() {}

  /* ADVICE for Methods */

  /**
   * Around advice that intercepts calls to non-static void methods and forwards them to {@link
   * DispatchForwarder#voidInstanceMethod}.
   *
   * @param pjp the proceeding join point representing the method call
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidInstanceMethods()")
  public void aroundVoidInstanceMethods(ProceedingJoinPoint pjp) throws Throwable {
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> void instance method: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
      logger.debug(parametersToString(pjp));
    }

    String prev = TL_CURRENT_CALL_SIG.get();
    TL_CURRENT_CALL_SIG.set(computeMethodGuardKey(pjp));
    try {
      DispatchForwarder.voidInstanceMethod(pjp);
    } finally {
      TL_CURRENT_CALL_SIG.set(prev);
    }
  }

  /**
   * Around advice that intercepts calls to static void methods and forwards them to {@link
   * DispatchForwarder#voidClassMethod}.
   *
   * @param pjp the proceeding join point representing the method call
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidClassMethods()")
  public void aroundVoidClassMethods(ProceedingJoinPoint pjp) throws Throwable {
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> void class method: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
      logger.debug(parametersToString(pjp));
    }

    String prev = TL_CURRENT_CALL_SIG.get();
    TL_CURRENT_CALL_SIG.set(computeMethodGuardKey(pjp));
    try {
      DispatchForwarder.voidClassMethod(pjp);
    } finally {
      TL_CURRENT_CALL_SIG.set(prev);
    }
  }

  /**
   * Around advice that intercepts calls to non-static methods returning a value and forwards them
   * to {@link DispatchForwarder#nonVoidInstanceMethod}.
   *
   * @param pjp the proceeding join point representing the method call
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidInstanceMethods()")
  public Object aroundNonVoidInstanceMethods(ProceedingJoinPoint pjp) throws Throwable {
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> non-void instance method: {}",
          pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
      logger.debug(parametersToString(pjp));
    }

    String prev = TL_CURRENT_CALL_SIG.get();
    TL_CURRENT_CALL_SIG.set(computeMethodGuardKey(pjp));
    try {
      return DispatchForwarder.nonVoidInstanceMethod(pjp);
    } finally {
      TL_CURRENT_CALL_SIG.set(prev);
    }
  }

  /**
   * Around advice that intercepts calls to static methods returning a value and forwards them to
   * {@link DispatchForwarder#nonVoidClassMethod}.
   *
   * @param pjp the proceeding join point representing the method call
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidClassMethods()")
  public Object aroundNonVoidClassMethods(ProceedingJoinPoint pjp) throws Throwable {
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> non-void class method: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
      logger.debug(parametersToString(pjp));
    }

    String prev = TL_CURRENT_CALL_SIG.get();
    TL_CURRENT_CALL_SIG.set(computeMethodGuardKey(pjp));
    try {
      return DispatchForwarder.nonVoidClassMethod(pjp);
    } finally {
      TL_CURRENT_CALL_SIG.set(prev);
    }
  }

  /* ADVICE for Constructors */

  /**
   * Around advice that intercepts constructor calls and forwards them to {@link
   * DispatchForwarder#constructor}.
   *
   * <p>Unlike the method advice, the constructor advice does <strong>not</strong> touch {@link
   * #TL_CURRENT_CALL_SIG}. No {@code execution(new(..))} pointcut is declared (AspectJ's
   * {@code @Around} on constructor execution wraps the body in a synthetic method, breaking {@code
   * final} field assignment — see the note on the execution-site advice block), so there is no
   * exec-site consumer to pair with. Any method calls made from within the constructor body perform
   * their own save/restore of the slot and therefore preserve the outer caller's guard value on
   * their own.
   *
   * @param pjp the proceeding join point representing the constructor call
   * @return the constructed object or a replacement provided by the dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("constructors()")
  public Object aroundConstructors(ProceedingJoinPoint pjp) throws Throwable {
    if (logger.isDebugEnabled()) {
      logger.debug(" D --> constructor: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
      logger.debug(parametersToString(pjp));
    }

    return DispatchForwarder.constructor(pjp);
  }

  /*
   * ADVICE for Method Executions (supplement call-site advice).
   *
   * Execution-site advice runs inside the callee's body, so it captures method invocations that
   * never hit a woven call-site (reflection, method references, lambdas, invokedynamic, JNI,
   * external unwoven code). In those cases the original caller is unwoven or synthetic, so the
   * dispatched operation records {@code sender == target} — the only PAL-visible peer on the stack
   * is the callee itself.
   *
   * <p>When a woven call-site has already dispatched for this exact invocation on this thread,
   * {@link #TL_CURRENT_CALL_SIG} holds that call-site's join-point signature. Execution-site advice
   * compares its own signature to the thread-local; an exact match means the call-site already
   * dispatched and this advice must simply {@code proceed} to avoid double-recording. Any other
   * state (null, or a different signature left by an outer unrelated call-site such as {@code
   * Method.invoke} or {@code Function.apply}) means the caller was unwoven for THIS method, and
   * the execution-site must dispatch.
   *
   * <p><strong>Note on constructors:</strong> no execution-site advice is declared for {@code
   * execution(new(..))}. AspectJ's {@code @Around} over constructor execution must wrap the body
   * in a synthetic method, which prevents {@code final} field assignments from reaching {@code
   * <init>} and raises {@code IllegalAccessError}. Reflective constructor invocation is therefore
   * observable only indirectly via the surrounding {@code Constructor.newInstance} call-site.
   */

  /**
   * Around advice that intercepts executions of non-static void methods and, when the call-site
   * advice has not already dispatched for this exact invocation on this thread, forwards them to
   * {@link DispatchForwarder#voidInstanceMethod}.
   *
   * @param pjp the proceeding join point representing the method execution
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidInstanceMethodsExec()")
  public void aroundVoidInstanceMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (callSiteAlreadyDispatched(pjp)) {
      pjp.proceed();
      return;
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> void instance method (exec): {}",
          pjp.getStaticPart().getSignature().toShortString());
    }
    DispatchForwarder.voidInstanceMethod(pjp);
  }

  /**
   * Around advice that intercepts executions of static void methods and, when the call-site advice
   * has not already dispatched for this exact invocation on this thread, forwards them to {@link
   * DispatchForwarder#voidClassMethod}.
   *
   * @param pjp the proceeding join point representing the method execution
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidClassMethodsExec()")
  public void aroundVoidClassMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (callSiteAlreadyDispatched(pjp)) {
      pjp.proceed();
      return;
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> void class method (exec): {}",
          pjp.getStaticPart().getSignature().toShortString());
    }
    DispatchForwarder.voidClassMethod(pjp);
  }

  /**
   * Around advice that intercepts executions of non-static methods returning a value and, when the
   * call-site advice has not already dispatched for this exact invocation on this thread, forwards
   * them to {@link DispatchForwarder#nonVoidInstanceMethod}.
   *
   * @param pjp the proceeding join point representing the method execution
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidInstanceMethodsExec()")
  public Object aroundNonVoidInstanceMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (callSiteAlreadyDispatched(pjp)) {
      return pjp.proceed();
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> non-void instance method (exec): {}",
          pjp.getStaticPart().getSignature().toShortString());
    }
    return DispatchForwarder.nonVoidInstanceMethod(pjp);
  }

  /**
   * Around advice that intercepts executions of static methods returning a value and, when the
   * call-site advice has not already dispatched for this exact invocation on this thread, forwards
   * them to {@link DispatchForwarder#nonVoidClassMethod}.
   *
   * @param pjp the proceeding join point representing the method execution
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidClassMethodsExec()")
  public Object aroundNonVoidClassMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (callSiteAlreadyDispatched(pjp)) {
      return pjp.proceed();
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          " D --> non-void class method (exec): {}",
          pjp.getStaticPart().getSignature().toShortString());
    }
    return DispatchForwarder.nonVoidClassMethod(pjp);
  }

  /**
   * Returns {@code true} when the execution-site advice must skip dispatch and simply proceed,
   * because something has already claimed this invocation on the current thread:
   *
   * <ul>
   *   <li>A woven call-site whose guard key matches this execution-site's own — the normal
   *       woven-to-woven direct-call case. The key is computed from the runtime receiver class (see
   *       {@link #computeMethodGuardKey(JoinPoint)}), so virtual-dispatch, interface-dispatch and
   *       {@code super} calls all produce agreeing keys on both sides of the invocation.
   *   <li>The PAL runtime's reflective invocation path, which installs {@link
   *       #RUNTIME_INVOKE_SENTINEL} before calling {@code Method.invoke} on an incoming-message
   *       target and restores the previous value afterwards.
   * </ul>
   *
   * <p><strong>The sentinel check uses {@code ==} on purpose.</strong> Reference identity
   * guarantees that only the exact constant installed by {@link #beginRuntimeInvoke()} trips the
   * short-circuit. A {@code .equals(...)} check would allow any equal-content string — including
   * one derived from user data, a deserialised message, or a maliciously crafted class name — to
   * impersonate the sentinel and cause exec-site advice to silently skip dispatch. Do not change
   * this comparison.
   *
   * <p>The same {@code ==} identity check is also used as a fast path for the key comparison:
   * because {@link #computeMethodGuardKey(JoinPoint)} canonicalises every key through {@link
   * #INTERNED_GUARD_KEYS}, a woven-to-woven direct call observes the <em>same</em> {@link String}
   * instance at both the call-site and the execution-site, and the reference check succeeds without
   * a character walk. The {@code .equals(...)} fallback is retained to accept any value-equal
   * string the test harness installs directly in the slot.
   *
   * @param pjp the join point whose execution-site advice is consulting the guard
   * @return whether the exec-site advice should skip dispatch and simply proceed
   */
  @SuppressFBWarnings(
      value = "ES_COMPARING_STRINGS_WITH_EQ",
      justification =
          "Identity comparison is load-bearing: guard keys flow through a ClassValue cache and an"
              + " intern map that canonicalizes them, so matching call- and exec-sites share the"
              + " same String instance. The == check is a fast path; equals() remains as the"
              + " correctness fallback.")
  private static boolean callSiteAlreadyDispatched(JoinPoint pjp) {
    String current = TL_CURRENT_CALL_SIG.get();
    if (current == null) {
      return false;
    }
    // Identity comparison is load-bearing: see javadoc on RUNTIME_INVOKE_SENTINEL and this method.
    if (current == RUNTIME_INVOKE_SENTINEL) {
      return true;
    }
    String mine = computeMethodGuardKey(pjp);
    return current == mine || current.equals(mine);
  }

  /**
   * Computes the thread-local guard key used to pair a method call-site advice with the matching
   * execution-site advice on the same invocation.
   *
   * <p>The key format is {@code <class-name>#<method-name>(<param-1>,<param-2>,...)}, where:
   *
   * <ul>
   *   <li>For instance methods, {@code class-name} is the <strong>runtime class of the receiver
   *       ({@link JoinPoint#getTarget()})</strong>. At a call-site this is the actual object about
   *       to dispatch (not the static compile-time type), and at an execution-site this is {@code
   *       this}. The two sides therefore agree even when the call-site's static receiver type is a
   *       supertype of the actual runtime class — i.e. virtual dispatch on an override ({@code Base
   *       b = new Sub(); b.foo()}), interface dispatch ({@code Iface i = new Impl(); i.m()}), and
   *       {@code super.foo()} calls (where the exec-site is the base method but {@code
   *       this.getClass()} is still the concrete subclass).
   *   <li>For static methods (and whenever the target is {@code null}), {@code class-name} is the
   *       signature's declaring type, which is stable across call and execution.
   * </ul>
   *
   * <p>Using {@link org.aspectj.lang.Signature#toLongString()} instead would key on the static
   * compile-time receiver type, causing a mismatch between the call-site and the execution-site
   * under virtual/interface dispatch and yielding double-dispatch.
   *
   * <p><strong>Hot-path caching.</strong> The computed key is memoized in {@link #GUARD_KEY_CACHE}
   * keyed by (runtime class, {@link JoinPoint.StaticPart}). On a cache hit (the steady-state case)
   * no allocation occurs. On a miss, the built string is canonicalised through {@link
   * #INTERNED_GUARD_KEYS}, so the call-site's cache entry and the exec-site's cache entry for the
   * same (class, method-descriptor) share a single {@code String} instance — enabling the {@code
   * ==} fast path in {@link #callSiteAlreadyDispatched(JoinPoint)}.
   *
   * @param pjp the method call-site or execution-site join point to key
   * @return a stable guard key that agrees on both sides of a woven-to-woven invocation
   */
  static String computeMethodGuardKey(JoinPoint pjp) {
    MethodSignature ms = (MethodSignature) pjp.getSignature();
    Object target = pjp.getTarget();
    Class<?> keyClass =
        (target != null && !Modifier.isStatic(ms.getModifiers()))
            ? target.getClass()
            : ms.getDeclaringType();
    ConcurrentHashMap<JoinPoint.StaticPart, String> forClass = GUARD_KEY_CACHE.get(keyClass);
    JoinPoint.StaticPart sp = pjp.getStaticPart();
    String cached = forClass.get(sp);
    if (cached != null) {
      return cached;
    }
    String built = buildMethodGuardKey(keyClass, ms);
    String canonical = INTERNED_GUARD_KEYS.computeIfAbsent(built, k -> k);
    String prev = forClass.putIfAbsent(sp, canonical);
    return prev != null ? prev : canonical;
  }

  /**
   * Builds the guard-key string for the given runtime class and method signature. Separated from
   * {@link #computeMethodGuardKey(JoinPoint)} so the hot path can remain tight and the miss path
   * can be inlined independently.
   *
   * @param keyClass the class whose name forms the first segment of the key
   * @param ms the method signature from which the method name and parameter types are read
   * @return the freshly-built key string (not yet interned)
   */
  private static String buildMethodGuardKey(Class<?> keyClass, MethodSignature ms) {
    StringBuilder sb = new StringBuilder();
    sb.append(keyClass.getName()).append('#').append(ms.getName()).append('(');
    Class<?>[] params = ms.getParameterTypes();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(params[i].getName());
    }
    sb.append(')');
    return sb.toString();
  }

  /* ADVICE for Fields */

  /**
   * Around advice that intercepts get access to static fields and forwards them to {@link
   * DispatchForwarder#getStatic}.
   *
   * @param pjp the proceeding join point representing the field access
   * @return the value obtained from the field or dispatcher
   */
  @Around("staticGetfields()")
  public Object aroundStaticGetfields(ProceedingJoinPoint pjp) {
    if (logger.isDebugEnabled()) {
      logger.debug(" D --> get static: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
    }

    return DispatchForwarder.getStatic(pjp);
  }

  /**
   * Around advice that intercepts get access to non-static fields and forwards them to {@link
   * DispatchForwarder#getObject}.
   *
   * @param pjp the proceeding join point representing the field access
   * @return the value obtained from the field or dispatcher
   */
  @Around("nonStaticGetfields()")
  public Object aroundNonStaticGetfields(ProceedingJoinPoint pjp) {
    if (logger.isDebugEnabled()) {
      logger.debug(" D --> get field: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
    }

    return DispatchForwarder.getObject(pjp);
  }

  /**
   * Around advice that intercepts set access to static, non-final fields and forwards them to
   * {@link DispatchForwarder#putStatic}.
   *
   * @param pjp the proceeding join point representing the field write
   */
  @Around("staticPutfields()")
  public void aroundStaticPutfields(ProceedingJoinPoint pjp) {
    if (logger.isDebugEnabled()) {
      logger.debug(" D --> put static: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
    }

    DispatchForwarder.putStatic(pjp);
  }

  /**
   * Around advice that intercepts set access to non-static, non-final fields and forwards them to
   * {@link DispatchForwarder#putField}.
   *
   * @param pjp the proceeding join point representing the field write
   */
  @Around("nonStaticPutfields()")
  public void aroundNonStaticPutfields(ProceedingJoinPoint pjp) {
    if (logger.isDebugEnabled()) {
      logger.debug(" D --> put field: {}", pjp.getStaticPart().getSignature().toShortString());
      logger.debug(staticCtxtToString(pjp.getStaticPart()));
      logger.debug(nonStaticCtxtToString(pjp));
    }

    DispatchForwarder.putField(pjp);
  }

  /**
   * Builds static join point context information such as id, kind, and source location.
   *
   * @param jpsp the static part of the join point to describe
   * @return a String with the formatted static context
   */
  private static String staticCtxtToString(StaticPart jpsp) {
    StringBuilder sb = new StringBuilder();
    String nl = System.lineSeparator();

    sb.append(" ... jp.id=").append(jpsp.getId()).append(nl);
    sb.append(" ... jp.kind=").append(jpsp.getKind()).append(nl);
    sb.append(" ... jp.signature=").append(jpsp.getSignature().toShortString()).append(nl);
    sb.append(" ... jp.source=").append(jpsp.getSourceLocation()).append(nl);
    sb.append(" ... jp.toLongString=").append(jpsp.toLongString());

    return sb.toString();
  }

  /**
   * Builds non-static join point context, namely the target object and {@code this} reference.
   *
   * @param jp the join point whose context should be represented
   * @return a String with the formatted non-static context
   */
  private static String nonStaticCtxtToString(JoinPoint jp) {
    StringBuilder sb = new StringBuilder();
    String nl = System.lineSeparator();

    sb.append(" --- target object=").append(jp.getTarget()).append(nl);
    sb.append(" --- this=").append(jp.getThis());

    return sb.toString();
  }

  /**
   * Builds the argument names, types and values for the given join point.
   *
   * @param jp the join point whose parameter information should be represented
   * @return a String with the formatted parameters (or empty String if none)
   */
  private static String parametersToString(JoinPoint jp) {
    Object[] args = jp.getArgs();
    String[] names = ((CodeSignature) jp.getSignature()).getParameterNames();
    Class<?>[] types = ((CodeSignature) jp.getSignature()).getParameterTypes();

    if (args.length == 0) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    String nl = System.lineSeparator();

    sb.append(" --- Arguments: ").append(nl);
    for (int i = 0; i < args.length; i++) {
      sb.append(" ---   ")
          .append(i)
          .append(". ")
          .append(names[i])
          .append(" : ")
          .append(types[i].getName())
          .append(" = ")
          .append(args[i])
          .append(nl);
    }

    // Remove trailing newline
    if (!sb.isEmpty()) {
      sb.setLength(sb.length() - nl.length());
    }

    return sb.toString();
  }
}
