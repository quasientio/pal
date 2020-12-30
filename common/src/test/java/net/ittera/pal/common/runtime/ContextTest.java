/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.runtime;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("rawtypes")
public class ContextTest {
  static class ClassForConstructorTest {
    public ClassForConstructorTest(Integer intParam) {}
  }

  static class ClassForMethodTest {
    public void myMethod(String text) {}
  }

  static class ClassForFieldTest {
    protected int myIntField;
  }

  static class ContextTestArgs {
    final String sourceFilename;
    final int sourceLine;
    final Class withinType;
    final AccessibleObject accessibleObject;
    private final Signature signature;

    ContextTestArgs(
        String sourceFilename,
        int sourceLine,
        Class withinType,
        AccessibleObject accessibleObject,
        Signature signature) {
      this.sourceFilename = sourceFilename;
      this.sourceLine = sourceLine;
      this.withinType = withinType;
      this.accessibleObject = accessibleObject;
      this.signature = signature;
    }

    Context createContext() {
      return new Context(sourceFilename, sourceLine, withinType, signature);
    }
  }

  private ContextTestArgs constructorArgs, methodArgs, fieldArgs;

  @Before
  public void setUp() throws Exception {
    AccessibleObject accessibleObject =
        ClassForConstructorTest.class.getDeclaredConstructor(Integer.class);
    constructorArgs =
        new ContextTestArgs(
            "NotARealClass1.java",
            11,
            ClassForConstructorTest.class,
            accessibleObject,
            new ConstructorSignature((Constructor) accessibleObject));

    accessibleObject = ClassForMethodTest.class.getDeclaredMethod("myMethod", String.class);
    methodArgs =
        new ContextTestArgs(
            "NotARealClass2.java",
            23,
            ClassForMethodTest.class,
            accessibleObject,
            new MethodSignature((Method) accessibleObject));

    accessibleObject = ClassForFieldTest.class.getDeclaredField("myIntField");
    fieldArgs =
        new ContextTestArgs(
            "NotARealClass3.java",
            300,
            ClassForFieldTest.class,
            accessibleObject,
            new FieldSignature((Field) accessibleObject));
  }

  @Test
  public void getFileName() {
    assertEquals(
        constructorArgs.sourceFilename, constructorArgs.createContext().getSourceFilename());
    assertEquals(methodArgs.sourceFilename, methodArgs.createContext().getSourceFilename());
    assertEquals(fieldArgs.sourceFilename, fieldArgs.createContext().getSourceFilename());
  }

  @Test
  public void getSourceLine() {
    assertEquals(constructorArgs.sourceLine, constructorArgs.createContext().getSourceLine());
    assertEquals(methodArgs.sourceLine, methodArgs.createContext().getSourceLine());
    assertEquals(fieldArgs.sourceLine, fieldArgs.createContext().getSourceLine());
  }

  @Test
  public void getWithinType() {
    assertEquals(constructorArgs.withinType, constructorArgs.createContext().getWithinType());
    assertEquals(methodArgs.withinType, methodArgs.createContext().getWithinType());
    assertEquals(fieldArgs.withinType, fieldArgs.createContext().getWithinType());
  }

  @Test
  public void getSignature() {
    assertEquals(constructorArgs.signature, constructorArgs.createContext().getSignature());
    assertEquals(methodArgs.signature, methodArgs.createContext().getSignature());
    assertEquals(fieldArgs.signature, fieldArgs.createContext().getSignature());
  }

  private static SourceLocation getStubbedSourceLocation(ContextTestArgs contextArgs) {
    SourceLocation mockedSourceLocation = mock(SourceLocation.class);
    when(mockedSourceLocation.getFileName()).thenReturn(contextArgs.sourceFilename);
    when(mockedSourceLocation.getLine()).thenReturn(contextArgs.sourceLine);
    when(mockedSourceLocation.getWithinType()).thenReturn(contextArgs.withinType);
    return mockedSourceLocation;
  }

  private static JoinPoint.StaticPart getSubbedStaticPart(
      SourceLocation sourceLocation, org.aspectj.lang.reflect.MemberSignature signature) {
    JoinPoint.StaticPart mockedStaticPart = mock(JoinPoint.StaticPart.class);
    when(mockedStaticPart.getSourceLocation()).thenReturn(sourceLocation);
    when(mockedStaticPart.getSignature()).thenReturn(signature);
    return mockedStaticPart;
  }

  @Test
  public void parseFrom_constructor() {
    Constructor constructor = (Constructor) constructorArgs.accessibleObject;

    // stub SourceLocation
    SourceLocation mockedSourceLocation = getStubbedSourceLocation(constructorArgs);

    // stub ConstructorSignature
    org.aspectj.lang.reflect.ConstructorSignature mockedConstructorSignature =
        mock(org.aspectj.lang.reflect.ConstructorSignature.class);
    when(mockedConstructorSignature.getDeclaringType()).thenReturn(ClassForConstructorTest.class);
    when(mockedConstructorSignature.getDeclaringTypeName())
        .thenReturn("net.ittera.pal.common.runtime.ContextTest$ClassForConstructorTest");
    when(mockedConstructorSignature.getModifiers()).thenReturn(Modifier.PUBLIC);
    when(mockedConstructorSignature.getName())
        .thenReturn("net.ittera.pal.common.runtime.ContextTest$ClassForConstructorTest");
    when(mockedConstructorSignature.getExceptionTypes()).thenReturn(new Class[] {});

    // arg0, since when creating ConstructorSignature from constructor object there are no param
    // names
    when(mockedConstructorSignature.getParameterNames()).thenReturn(new String[] {"arg0"});
    when(mockedConstructorSignature.getParameterTypes()).thenReturn(new Class[] {Integer.class});
    when(mockedConstructorSignature.getConstructor()).thenReturn(constructor);

    // stub StaticPart
    JoinPoint.StaticPart mockedStaticPart =
        getSubbedStaticPart(mockedSourceLocation, mockedConstructorSignature);

    // call and verify
    Context parsedContext = Context.parseFrom(mockedStaticPart);
    assertEquals(constructorArgs.sourceFilename, parsedContext.getSourceFilename());
    assertEquals(constructorArgs.sourceLine, parsedContext.getSourceLine());
    assertEquals(constructorArgs.withinType, parsedContext.getWithinType());
    assertEquals(constructorArgs.signature, parsedContext.getSignature());
    assertEquals(
        constructor, ((ConstructorSignature) parsedContext.getSignature()).getConstructor());
  }

  @Test
  public void parseFrom_method() {
    Method method = (Method) methodArgs.accessibleObject;

    // stub SourceLocation
    SourceLocation mockedSourceLocation = getStubbedSourceLocation(methodArgs);

    // stub MethodSignature
    org.aspectj.lang.reflect.MethodSignature mockedMethodSignature =
        mock(org.aspectj.lang.reflect.MethodSignature.class);
    when(mockedMethodSignature.getDeclaringType()).thenReturn(ClassForMethodTest.class);
    when(mockedMethodSignature.getDeclaringTypeName())
        .thenReturn("net.ittera.pal.common.runtime.ContextTest$ClassForMethodTest");
    when(mockedMethodSignature.getModifiers()).thenReturn(Modifier.PUBLIC);
    when(mockedMethodSignature.getName()).thenReturn("myMethod");
    when(mockedMethodSignature.getExceptionTypes()).thenReturn(new Class[] {});
    when(mockedMethodSignature.getMethod()).thenReturn(method);
    when(mockedMethodSignature.getReturnType()).thenReturn(method.getReturnType());

    // arg0, since when creating ConstructorSignature from constructor object there are no param
    // names
    when(mockedMethodSignature.getParameterNames()).thenReturn(new String[] {"arg0"});
    when(mockedMethodSignature.getParameterTypes()).thenReturn(new Class[] {String.class});
    when(mockedMethodSignature.getMethod()).thenReturn(method);

    // stub StaticPart
    JoinPoint.StaticPart mockedStaticPart =
        getSubbedStaticPart(mockedSourceLocation, mockedMethodSignature);

    // call and verify
    Context parsedContext = Context.parseFrom(mockedStaticPart);
    assertEquals(methodArgs.sourceFilename, parsedContext.getSourceFilename());
    assertEquals(methodArgs.sourceLine, parsedContext.getSourceLine());
    assertEquals(methodArgs.withinType, parsedContext.getWithinType());
    assertEquals(methodArgs.signature, parsedContext.getSignature());
  }

  @Test
  public void parseFrom_field() {
    Field field = (Field) fieldArgs.accessibleObject;

    // stub SourceLocation
    SourceLocation mockedSourceLocation = getStubbedSourceLocation(fieldArgs);

    // stub FieldSignature
    org.aspectj.lang.reflect.FieldSignature mockedFieldSignature =
        mock(org.aspectj.lang.reflect.FieldSignature.class);
    when(mockedFieldSignature.getDeclaringType()).thenReturn(ClassForFieldTest.class);
    when(mockedFieldSignature.getDeclaringTypeName())
        .thenReturn("net.ittera.pal.common.runtime.ContextTest$ClassForFieldTest");
    when(mockedFieldSignature.getModifiers()).thenReturn(Modifier.PROTECTED);
    when(mockedFieldSignature.getName()).thenReturn("myIntField");
    when(mockedFieldSignature.getField()).thenReturn(field);
    when(mockedFieldSignature.getFieldType()).thenReturn(field.getType());

    // stub StaticPart
    JoinPoint.StaticPart mockedStaticPart =
        getSubbedStaticPart(mockedSourceLocation, mockedFieldSignature);

    // call and verify
    Context parsedContext = Context.parseFrom(mockedStaticPart);
    assertEquals(fieldArgs.sourceFilename, parsedContext.getSourceFilename());
    assertEquals(fieldArgs.sourceLine, parsedContext.getSourceLine());
    assertEquals(fieldArgs.withinType, parsedContext.getWithinType());
    assertEquals(fieldArgs.signature, parsedContext.getSignature());
  }

  @Test
  public void parseFrom_invalidSignatureClass() {

    // stub SourceLocation
    SourceLocation mockedSourceLocation = getStubbedSourceLocation(fieldArgs);

    // create unsupported org.aspectj.lang.Signature subclass
    class MySignature implements org.aspectj.lang.Signature {
      @Override
      public String toShortString() {
        return null;
      }

      @Override
      public String toLongString() {
        return null;
      }

      @Override
      public String getName() {
        return null;
      }

      @Override
      public int getModifiers() {
        return 0;
      }

      @Override
      public Class getDeclaringType() {
        return null;
      }

      @Override
      public String getDeclaringTypeName() {
        return null;
      }
    }

    // stub StaticPart
    JoinPoint.StaticPart mockedStaticPart = mock(JoinPoint.StaticPart.class);
    when(mockedStaticPart.getSourceLocation()).thenReturn(mockedSourceLocation);
    when(mockedStaticPart.getSignature()).thenReturn(new MySignature());

    // call
    try {
      Context.parseFrom(mockedStaticPart);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException ignored) {
      // all good
    }
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(Context.class).usingGetClass().verify();
  }

  @Test
  public void testToString() {
    Stream.of(constructorArgs, methodArgs, fieldArgs)
        .forEach(
            ctxtArgs ->
                assertThat(
                    ctxtArgs.createContext().toString(),
                    is(
                        "Context{"
                            + "sourceFilename='"
                            + ctxtArgs.sourceFilename
                            + '\''
                            + ", sourceLine="
                            + ctxtArgs.sourceLine
                            + ", withinType="
                            + ctxtArgs.withinType
                            + ", signature="
                            + ctxtArgs.signature
                            + '}')));
  }
}
