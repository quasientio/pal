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

import io.quasient.pal.common.runtime.DispatchForwarder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
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
   * Tracks the nesting depth of active call-site advice on the current thread.
   *
   * <p>Each call-site advice increments this counter before invoking the {@link DispatchForwarder}
   * and decrements it in a {@code finally} block on return (including exceptional return). A
   * counter (rather than a boolean) is required to correctly handle nested woven-to-woven calls,
   * where multiple call-site advice frames are simultaneously active on the same thread.
   *
   * <p>Execution-site advice consults this counter to avoid double-dispatch: when the counter is
   * positive, the call-site advice has already dispatched and the execution-site advice must simply
   * {@code proceed}.
   */
  static final ThreadLocal<Integer> TL_CALL_ADVICE_DEPTH = ThreadLocal.withInitial(() -> 0);

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

  /**
   * Matches constructor executions in the selected classes.
   *
   * <p>Supplements {@link #constructors()} by firing on the callee side, capturing constructions
   * originating from unwoven callers (e.g. reflection, serialization, or framework instantiation).
   */
  @Pointcut("allClasses() && execution(new(..))")
  private void constructorsExec() {}

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

    TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() + 1);
    try {
      DispatchForwarder.voidInstanceMethod(pjp);
    } finally {
      TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() - 1);
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

    TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() + 1);
    try {
      DispatchForwarder.voidClassMethod(pjp);
    } finally {
      TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() - 1);
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

    TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() + 1);
    try {
      return DispatchForwarder.nonVoidInstanceMethod(pjp);
    } finally {
      TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() - 1);
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

    TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() + 1);
    try {
      return DispatchForwarder.nonVoidClassMethod(pjp);
    } finally {
      TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() - 1);
    }
  }

  /* ADVICE for Constructors */

  /**
   * Around advice that intercepts constructor calls and forwards them to {@link
   * DispatchForwarder#constructor}.
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

    TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() + 1);
    try {
      return DispatchForwarder.constructor(pjp);
    } finally {
      TL_CALL_ADVICE_DEPTH.set(TL_CALL_ADVICE_DEPTH.get() - 1);
    }
  }

  /* ADVICE for Method/Constructor Executions (supplement call-site advice) */

  /**
   * Around advice that intercepts executions of non-static void methods and, when no call-site
   * advice is active on this thread, forwards them to {@link DispatchForwarder#voidInstanceMethod}.
   *
   * <p>When {@link #TL_CALL_ADVICE_DEPTH} is positive, the call-site advice has already dispatched
   * for this invocation and this advice simply proceeds to avoid double-dispatch.
   *
   * @param pjp the proceeding join point representing the method execution
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidInstanceMethodsExec()")
  public void aroundVoidInstanceMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (TL_CALL_ADVICE_DEPTH.get() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            " D --> void instance method (exec): {}",
            pjp.getStaticPart().getSignature().toShortString());
      }
      DispatchForwarder.voidInstanceMethod(pjp);
    } else {
      pjp.proceed();
    }
  }

  /**
   * Around advice that intercepts executions of static void methods and, when no call-site advice
   * is active on this thread, forwards them to {@link DispatchForwarder#voidClassMethod}.
   *
   * <p>When {@link #TL_CALL_ADVICE_DEPTH} is positive, the call-site advice has already dispatched
   * for this invocation and this advice simply proceeds to avoid double-dispatch.
   *
   * @param pjp the proceeding join point representing the method execution
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("voidClassMethodsExec()")
  public void aroundVoidClassMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (TL_CALL_ADVICE_DEPTH.get() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            " D --> void class method (exec): {}",
            pjp.getStaticPart().getSignature().toShortString());
      }
      DispatchForwarder.voidClassMethod(pjp);
    } else {
      pjp.proceed();
    }
  }

  /**
   * Around advice that intercepts executions of non-static methods returning a value and, when no
   * call-site advice is active on this thread, forwards them to {@link
   * DispatchForwarder#nonVoidInstanceMethod}.
   *
   * <p>When {@link #TL_CALL_ADVICE_DEPTH} is positive, the call-site advice has already dispatched
   * for this invocation and this advice simply proceeds, returning the result of {@code
   * pjp.proceed()} to avoid double-dispatch.
   *
   * @param pjp the proceeding join point representing the method execution
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidInstanceMethodsExec()")
  public Object aroundNonVoidInstanceMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (TL_CALL_ADVICE_DEPTH.get() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            " D --> non-void instance method (exec): {}",
            pjp.getStaticPart().getSignature().toShortString());
      }
      return DispatchForwarder.nonVoidInstanceMethod(pjp);
    }
    return pjp.proceed();
  }

  /**
   * Around advice that intercepts executions of static methods returning a value and, when no
   * call-site advice is active on this thread, forwards them to {@link
   * DispatchForwarder#nonVoidClassMethod}.
   *
   * <p>When {@link #TL_CALL_ADVICE_DEPTH} is positive, the call-site advice has already dispatched
   * for this invocation and this advice simply proceeds, returning the result of {@code
   * pjp.proceed()} to avoid double-dispatch.
   *
   * @param pjp the proceeding join point representing the method execution
   * @return the result returned by the target method or dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("nonVoidClassMethodsExec()")
  public Object aroundNonVoidClassMethodsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (TL_CALL_ADVICE_DEPTH.get() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            " D --> non-void class method (exec): {}",
            pjp.getStaticPart().getSignature().toShortString());
      }
      return DispatchForwarder.nonVoidClassMethod(pjp);
    }
    return pjp.proceed();
  }

  /**
   * Around advice that intercepts constructor executions and, when no call-site advice is active on
   * this thread, forwards them to {@link DispatchForwarder#constructor}.
   *
   * <p>When {@link #TL_CALL_ADVICE_DEPTH} is positive, the call-site advice has already dispatched
   * for this invocation and this advice simply proceeds, returning the result of {@code
   * pjp.proceed()} to avoid double-dispatch.
   *
   * @param pjp the proceeding join point representing the constructor execution
   * @return the constructed object or a replacement provided by the dispatcher
   * @throws Throwable if the forwarding or target invocation fails
   */
  @Around("constructorsExec()")
  public Object aroundConstructorsExec(ProceedingJoinPoint pjp) throws Throwable {
    if (TL_CALL_ADVICE_DEPTH.get() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            " D --> constructor (exec): {}", pjp.getStaticPart().getSignature().toShortString());
      }
      return DispatchForwarder.constructor(pjp);
    }
    return pjp.proceed();
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
