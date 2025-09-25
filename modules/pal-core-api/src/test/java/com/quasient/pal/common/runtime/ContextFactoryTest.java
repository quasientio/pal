/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.runtime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.Test;

public class ContextFactoryTest {

  private static class SL implements SourceLocation {
    @Override
    public String getFileName() {
      return "X.java";
    }

    @Override
    public int getLine() {
      return 42;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getColumn() {
      return -1;
    }

    @Override
    public Class<?> getWithinType() {
      return ContextFactoryTest.class;
    }

    @Override
    public String toString() {
      return getFileName() + ":" + getLine();
    }
  }

  private abstract static class BaseSig implements Signature {
    final Class<?> decl;
    final String name;

    BaseSig(Class<?> d, String n) {
      this.decl = d;
      this.name = n;
    }

    @Override
    public String toShortString() {
      return name;
    }

    @Override
    public String toLongString() {
      return name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int getModifiers() {
      return 0;
    }

    @Override
    public Class<?> getDeclaringType() {
      return decl;
    }

    @Override
    public String getDeclaringTypeName() {
      return decl.getName();
    }
  }

  private static class MSig extends BaseSig implements MethodSignature {
    final Method m;

    MSig(Method m) {
      super(m.getDeclaringClass(), m.getName());
      this.m = m;
    }

    @Override
    public Method getMethod() {
      return m;
    }

    // Unused methods
    @Override
    public Class getReturnType() {
      return m.getReturnType();
    }

    @Override
    public Class[] getParameterTypes() {
      return m.getParameterTypes();
    }

    @Override
    public String[] getParameterNames() {
      return new String[0];
    }

    @Override
    public Class[] getExceptionTypes() {
      return new Class[0];
    }

    @Override
    public String toString() {
      return m.toString();
    }
  }

  private static class CSig extends BaseSig implements ConstructorSignature {
    final Constructor<?> c;

    CSig(Constructor<?> c) {
      super(c.getDeclaringClass(), c.getName());
      this.c = c;
    }

    @Override
    public Constructor getConstructor() {
      return c;
    }

    @Override
    public Class[] getParameterTypes() {
      return c.getParameterTypes();
    }

    @Override
    public String[] getParameterNames() {
      return new String[0];
    }

    @Override
    public Class[] getExceptionTypes() {
      return new Class[0];
    }

    @Override
    public String toString() {
      return c.toString();
    }
  }

  private static class FSig extends BaseSig implements FieldSignature {
    final Field f;

    FSig(Field f) {
      super(f.getDeclaringClass(), f.getName());
      this.f = f;
    }

    @Override
    public Field getField() {
      return f;
    }

    @Override
    public Class getFieldType() {
      return f.getType();
    }

    @Override
    public String toString() {
      return f.toString();
    }
  }

  private static class SP implements JoinPoint.StaticPart {
    final String kind;
    final Signature sig;
    final SourceLocation loc;

    SP(String kind, Signature sig) {
      this.kind = kind;
      this.sig = sig;
      this.loc = new SL();
    }

    @Override
    public String getKind() {
      return kind;
    }

    @Override
    public Signature getSignature() {
      return sig;
    }

    @Override
    public SourceLocation getSourceLocation() {
      return loc;
    }

    @Override
    public int getId() {
      return 1;
    }

    @Override
    public String toShortString() {
      return kind + ":" + sig;
    }

    @Override
    public String toLongString() {
      return toShortString();
    }

    @Override
    public String toString() {
      return toShortString();
    }
  }

  @Test
  public void perMember_methodExecution_cachedByMember() throws Exception {
    Method m = String.class.getMethod("length");
    JoinPoint.StaticPart sp = new SP(JoinPoint.METHOD_EXECUTION, new MSig(m));
    Context c1 = ContextFactory.forJoinPoint(sp);
    Context c2 = ContextFactory.forJoinPoint(sp);
    assertNotNull(c1);
    assertThat(c1, is(c2));
  }

  @Test
  public void perMember_constructorExecution_cachedByMember() throws Exception {
    Constructor<?> c = Object.class.getConstructor();
    JoinPoint.StaticPart sp = new SP(JoinPoint.CONSTRUCTOR_EXECUTION, new CSig(c));
    Context a = ContextFactory.forJoinPoint(sp);
    Context b = ContextFactory.forJoinPoint(sp);
    assertNotNull(a);
    assertThat(a, is(b));
  }

  @Test
  public void perMember_fieldGet_cachedByMember() throws Exception {
    Field f = System.class.getField("out");
    JoinPoint.StaticPart sp = new SP(JoinPoint.FIELD_GET, new FSig(f));
    Context a = ContextFactory.forJoinPoint(sp);
    Context b = ContextFactory.forJoinPoint(sp);
    assertNotNull(a);
    assertThat(a, is(b));
  }

  @Test
  public void perCallSite_methodCall_cachedByStaticPart() {
    try {
      Method m = String.class.getMethod("length");
      JoinPoint.StaticPart sp = new SP(JoinPoint.METHOD_CALL, new MSig(m));
      Context a = ContextFactory.forJoinPoint(sp);
      Context b = ContextFactory.forJoinPoint(sp);
      assertNotNull(a);
      assertThat(a, is(b));
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
