/**
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.weave;

import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.DispatchForwarder;
import com.quasient.pal.common.weave.Proceed;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

privileged aspect FullQuantizeAspect {

  //if false, no debug output at all
  private static final boolean verbose= Boolean.parseBoolean(System.getProperty("aspectj.debug", "false"));

  //Exception softening of calls to DispatchForwarder
  declare soft: Throwable : call (Object DispatchForwarder.constructor(..));
  declare soft: Throwable : call (void DispatchForwarder.voidInstanceMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidInstanceMethod(..));
  declare soft: Throwable : call (void DispatchForwarder.voidClassMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidClassMethod(..));

  /** POINTCUT DEFINITIONS **/

  pointcut allClasses():
      !within(FullQuantizeAspect) &&
      !within(com.quasient.pal.core..*) &&
      !within(is(EnumType));

  pointcut voidInstanceMethods(): allClasses() && call(!static void *(..));

  pointcut voidClassMethods(): allClasses() && call(static void *(..));

  pointcut nonVoidInstanceMethods(): allClasses() && call(!static !void *(..));

  pointcut nonVoidClassMethods(): allClasses() && call(static !void *(..));

  pointcut constructors(): allClasses() && call(new(..));

  pointcut staticGetfields(): allClasses() && get(static * *);

  pointcut nonStaticGetfields(): allClasses() && get(!static * *);

  pointcut staticPutfields(): allClasses() && set(static !final * *);

  pointcut nonStaticPutfields(): allClasses() && set(!static !final * *);

  /** ADVICE for Methods **/

  void around(): voidInstanceMethods() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
      if (verbose) {
            print(" D --> void instance method: "+thisJoinPointStaticPart.getSignature().toShortString());
            printStaticCtxt(thisJoinPointStaticPart);
            printNonStaticCtxt(thisJoinPoint);
            printParameters(thisJoinPoint);
      }

    DispatchForwarder.voidInstanceMethod(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  void around(): voidClassMethods() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> void class method: "+pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    DispatchForwarder.voidClassMethod(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  Object around(): nonVoidInstanceMethods() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> non-void instance method: "+pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.nonVoidInstanceMethod(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  Object around(): nonVoidClassMethods() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> non-void class method: "+pjp.getStaticPart().getSignature().toShortString());
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.nonVoidClassMethod(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  /** ADVICE for Constructors **/

  Object around(): constructors() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> constructor: "+pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
      printParameters(pjp);
    }

    return DispatchForwarder.constructor(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  /** ADVICE for Fields **/

  Object around(): staticGetfields() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> get static: "+pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    return DispatchForwarder.getStatic(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  Object around(): nonStaticGetfields() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> get field: "+pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    return DispatchForwarder.getObject(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  void around(): staticPutfields() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> put static: "+pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    DispatchForwarder.putStatic(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }

  void around(): nonStaticPutfields() {

    ProceedingJoinPoint pjp = (ProceedingJoinPoint) thisJoinPoint;
    if (verbose) {
      print(" D --> put field: "+pjp);
      printStaticCtxt(pjp.getStaticPart());
      printNonStaticCtxt(pjp);
    }

    DispatchForwarder.putField(Context.parseFrom(pjp.getStaticPart()), pjp, () -> proceed());
  }


  /** Utility methods **/

  static final void print(String s) {
    System.out.println(s);
  }

  static private void printStaticCtxt(JoinPoint.StaticPart jpsp) {
    print(" ... jp.id="+jpsp.getId());
    print(" ... jp.kind="+jpsp.getKind());
    print(" ... jp.signature="+jpsp.getSignature().toShortString());
    print(" ... jp.source="+jpsp.getSourceLocation());
    print(" ... jp.toLongString="+jpsp.toLongString());
  }

  static private void printNonStaticCtxt(JoinPoint jp) {
    print(" --- target object="+jp.getTarget());
    print(" --- this="+jp.getThis());
  }

  static private void printParameters(JoinPoint jp) {
    Object[] args = jp.getArgs();
    String[] names = ((CodeSignature)jp.getSignature()).getParameterNames();
    Class[] types = ((CodeSignature)jp.getSignature()).getParameterTypes();
    if (args.length>0) {
      print(" --- Arguments: " );
    }
    for (int i = 0; i < args.length; i++) {
      print(" ---   "  +i+ ". " +names[i]+ " : " +types[i].getName()+ " = " +args[i]);
    }
  }
}
