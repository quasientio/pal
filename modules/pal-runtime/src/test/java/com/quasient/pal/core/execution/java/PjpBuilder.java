/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.aspectj.lang.*;

/** Builds a ProceedingJoinPoint stub for dispatcher tests. */
public final class PjpBuilder {

  private final ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class, withSettings().lenient());
  private final JoinPoint.StaticPart sp =
      mock(JoinPoint.StaticPart.class, withSettings().lenient());
  private final org.aspectj.lang.reflect.SourceLocation sl =
      mock(org.aspectj.lang.reflect.SourceLocation.class, withSettings().lenient());

  private PjpBuilder() throws Throwable {
    // default PJP wiring
    doReturn(sp).when(pjp).getStaticPart();
    doThrow(new IllegalStateException("pjp.proceed() must not be used")).when(pjp).proceed();
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
    org.aspectj.lang.reflect.MethodSignature ms =
        mock(org.aspectj.lang.reflect.MethodSignature.class, withSettings().lenient());
    doReturn(m).when(ms).getMethod();
    doReturn(m.getDeclaringClass()).when(ms).getDeclaringType();
    doReturn(m.getDeclaringClass().getName()).when(ms).getDeclaringTypeName();
    doReturn(m.getModifiers()).when(ms).getModifiers();
    doReturn(m.getName()).when(ms).getName();
    doReturn(m.getExceptionTypes()).when(ms).getExceptionTypes();
    doReturn(m.getParameterTypes()).when(ms).getParameterTypes();
    doReturn(m.getReturnType()).when(ms).getReturnType();
    doReturn(ms).when(sp).getSignature();
    return this;
  }

  /** Use for execution JP of a constructor. */
  public PjpBuilder constructorExecutionSignature(Constructor<?> c) {
    org.aspectj.lang.reflect.ConstructorSignature cs =
        mock(org.aspectj.lang.reflect.ConstructorSignature.class, withSettings().lenient());
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
    org.aspectj.lang.reflect.FieldSignature fs =
        mock(org.aspectj.lang.reflect.FieldSignature.class, withSettings().lenient());
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

  public ProceedingJoinPoint build() {
    return pjp;
  }
}
