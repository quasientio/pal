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

/**
 * Aspect which forwards all method, constructor and field access join points to {@link
 * com.quasient.pal.common.runtime.DispatchForwarder} for handling.
 *
 * <p>The aspect is written in @AspectJ style and is intended to be used together with {@code
 * FullQuantizeSoftening.aj}, which declares soft exceptions for the {@code DispatchForwarder}
 * calls.
 */
@SuppressWarnings("unused")
@Aspect
public class FullQuantizeAspect {

  /** Flag that controls verbose debug logging for all join points handled by this aspect. */
  private static final boolean verbose =
      Boolean.parseBoolean(System.getProperty("aspectj.debug", "false"));

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
    if (verbose) {
      print(" D --> void instance method: " + pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    DispatchForwarder.voidInstanceMethod(pjp, pjp::proceed);
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
    if (verbose) {
      print(" D --> void class method: " + pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    DispatchForwarder.voidClassMethod(pjp, pjp::proceed);
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
    if (verbose) {
      print(
          " D --> non-void instance method: " + pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.nonVoidInstanceMethod(pjp, pjp::proceed);
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
    if (verbose) {
      print(" D --> non-void class method: " + pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.nonVoidClassMethod(pjp, pjp::proceed);
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
    if (verbose) {
      print(" D --> constructor: " + pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.constructor(pjp, pjp::proceed);
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
    if (verbose) {
      print(" D --> get static: " + pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    return DispatchForwarder.getStatic(pjp, pjp::proceed);
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
    if (verbose) {
      print(" D --> get field: " + pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    return DispatchForwarder.getObject(pjp, pjp::proceed);
  }

  /**
   * Around advice that intercepts set access to static, non-final fields and forwards them to
   * {@link DispatchForwarder#putStatic}.
   *
   * @param pjp the proceeding join point representing the field write
   */
  @Around("staticPutfields()")
  public void aroundStaticPutfields(ProceedingJoinPoint pjp) {
    if (verbose) {
      print(" D --> put static: " + pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    DispatchForwarder.putStatic(pjp, pjp::proceed);
  }

  /**
   * Around advice that intercepts set access to non-static, non-final fields and forwards them to
   * {@link DispatchForwarder#putField}.
   *
   * @param pjp the proceeding join point representing the field write
   */
  @Around("nonStaticPutfields()")
  public void aroundNonStaticPutfields(ProceedingJoinPoint pjp) {
    if (verbose) {
      print(" D --> put field: " + pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    DispatchForwarder.putField(pjp, pjp::proceed);
  }

  /* Utility methods */

  /**
   * Prints a single debug line to the standard output.
   *
   * @param s the message to print
   */
  static void print(String s) {
    System.out.println(s);
  }

  /**
   * Prints static join point context information such as id, kind, and source location.
   *
   * @param jpsp the static part of the join point to describe
   */
  private static void printStaticCtxt(StaticPart jpsp) {
    print(" ... jp.id=" + jpsp.getId());
    print(" ... jp.kind=" + jpsp.getKind());
    print(" ... jp.signature=" + jpsp.getSignature().toShortString());
    print(" ... jp.source=" + jpsp.getSourceLocation());
    print(" ... jp.toLongString=" + jpsp.toLongString());
  }

  /**
   * Prints non-static join point context, namely the target object and {@code this} reference.
   *
   * @param jp the join point whose context should be printed
   */
  private static void printNonStaticCtxt(JoinPoint jp) {
    print(" --- target object=" + jp.getTarget());
    print(" --- this=" + jp.getThis());
  }

  /**
   * Prints the argument names, types and values for the given join point.
   *
   * @param jp the join point whose parameter information should be printed
   */
  private static void printParameters(JoinPoint jp) {
    Object[] args = jp.getArgs();
    String[] names = ((CodeSignature) jp.getSignature()).getParameterNames();
    Class<?>[] types = ((CodeSignature) jp.getSignature()).getParameterTypes();
    if (args.length > 0) {
      print(" --- Arguments: ");
    }
    for (int i = 0; i < args.length; i++) {
      print(" ---   " + i + ". " + names[i] + " : " + types[i].getName() + " = " + args[i]);
    }
  }
}
