/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import org.aspectj.lang.*;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;

/** Builds a ProceedingJoinPoint stub for dispatcher tests. */
public final class PjpBuilder {

  private final ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
  private final JoinPoint.StaticPart sp = mock(JoinPoint.StaticPart.class);
  private final SourceLocation sl = mock(SourceLocation.class);

  private PjpBuilder() {
    // default PJP wiring
    when(pjp.getStaticPart()).thenReturn(sp);
    // pjp.proceed(Object[]) is used by BaseExecMessageDispatcher.invoke() to support argument
    // mutation. Tests must configure this using proceedBehavior() to delegate to their Proceed
    // callback. The default behavior (returns null) will indicate if a test forgot to configure it.
  }

  public static PjpBuilder create() throws Throwable {
    return new PjpBuilder();
  }

  // ----------- Source location -----------

  public PjpBuilder source(String fileName, int line, Class<?> withinType) {
    doReturn(fileName).when(sl).getFileName();
    doReturn(line).when(sl).getLine();
    doReturn(withinType).when(sl).getWithinType();
    doReturn(sl).when(sp).getSourceLocation();
    return this;
  }

  // ----------- Kinds -----------

  public PjpBuilder kindMethodCall() {
    doReturn(JoinPoint.METHOD_CALL).when(sp).getKind();
    return this;
  }

  public PjpBuilder kindConstructorCall() {
    doReturn(JoinPoint.CONSTRUCTOR_CALL).when(sp).getKind();
    return this;
  }

  public PjpBuilder kindFieldGet() {
    doReturn(JoinPoint.FIELD_GET).when(sp).getKind();
    return this;
  }

  public PjpBuilder kindFieldSet() {
    doReturn(JoinPoint.FIELD_SET).when(sp).getKind();
    return this;
  }

  // ----------- Signatures -----------

  /** Use for execution JP of a method. */
  public PjpBuilder methodExecutionSignature(Method m) {
    MethodSignature ms = mock(MethodSignature.class, withSettings().lenient());
    doReturn(m).when(ms).getMethod();
    doReturn(m.getDeclaringClass()).when(ms).getDeclaringType();
    doReturn(m.getDeclaringClass().getName()).when(ms).getDeclaringTypeName();
    doReturn(m.getModifiers()).when(ms).getModifiers();
    doReturn(m.getName()).when(ms).getName();
    doReturn(m.getExceptionTypes()).when(ms).getExceptionTypes();
    doReturn(m.getParameterTypes()).when(ms).getParameterTypes();
    doReturn(m.getReturnType()).when(ms).getReturnType();
    doReturn(ms).when(sp).getSignature();
    doReturn(ms).when(pjp).getSignature();
    return this;
  }

  /** Use for execution JP of a constructor. */
  public PjpBuilder constructorExecutionSignature(Constructor<?> c) {
    ConstructorSignature cs = mock(ConstructorSignature.class, withSettings().lenient());
    doReturn(c).when(cs).getConstructor();
    doReturn(c.getDeclaringClass()).when(cs).getDeclaringType();
    doReturn(c.getDeclaringClass().getName()).when(cs).getDeclaringTypeName();
    doReturn(c.getModifiers()).when(cs).getModifiers();
    doReturn(c.getName()).when(cs).getName();
    doReturn(c.getExceptionTypes()).when(cs).getExceptionTypes();
    doReturn(c.getParameterTypes()).when(cs).getParameterTypes();
    doReturn(cs).when(sp).getSignature();
    return this;
  }

  /** Use for field get/set execution JP. */
  public PjpBuilder fieldExecutionSignature(Field f) {
    FieldSignature fs = mock(FieldSignature.class, withSettings().lenient());
    doReturn(f).when(fs).getField();
    doReturn(f.getDeclaringClass()).when(fs).getDeclaringType();
    doReturn(f.getDeclaringClass().getName()).when(fs).getDeclaringTypeName();
    doReturn(f.getModifiers()).when(fs).getModifiers();
    doReturn(f.getName()).when(fs).getName();
    doReturn(f.getType()).when(fs).getFieldType();
    doReturn(fs).when(sp).getSignature();
    return this;
  }

  // ----------- PJP "dynamic" bits you already had -----------

  public PjpBuilder sender(Object sender) {
    doReturn(sender).when(pjp).getThis();
    return this;
  }

  public PjpBuilder target(Object target) {
    doReturn(target).when(pjp).getTarget();
    return this;
  }

  public PjpBuilder args(Object[] args) {
    doReturn(args).when(pjp).getArgs();
    return this;
  }

  /**
   * Configures pjp.proceed(Object[]) to delegate to a Callable callback.
   *
   * <p>This is needed for tests that verify argument mutation through intercepts. The default
   * behavior throws an exception to prevent accidental use of pjp.proceed().
   *
   * @param proceedCallback the callback to invoke when pjp.proceed(Object[]) is called
   * @return this builder
   */
  public <T> PjpBuilder proceedBehavior(Callable<T> proceedCallback) throws Throwable {
    when(pjp.proceed(any(Object[].class))).thenAnswer(inv -> proceedCallback.call());
    return this;
  }

  public ProceedingJoinPoint build() {
    return pjp;
  }
}
