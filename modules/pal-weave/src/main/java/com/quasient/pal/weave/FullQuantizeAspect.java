/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.weave;

import com.quasient.pal.common.runtime.DispatchForwarder;
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

  /* POINTCUT DEFINITIONS */

  /**
   * Matches all classes to be woven, excluding this aspect itself, core framework classes and enum
   * types.
   */
  @Pointcut(
      "!within(com.quasient.pal.weave.FullQuantizeAspect) && "
          + "!within(com.quasient.pal.core..*) && "
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

    DispatchForwarder.voidInstanceMethod(pjp);
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

    DispatchForwarder.voidClassMethod(pjp);
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

    return DispatchForwarder.nonVoidInstanceMethod(pjp);
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

    return DispatchForwarder.nonVoidClassMethod(pjp);
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

    return DispatchForwarder.constructor(pjp);
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
