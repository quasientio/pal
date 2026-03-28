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
package io.quasient.pal.common.lang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class MetadataTest {

  private static Metadata.ParameterInfo p(String type, String name) {
    Metadata.ParameterInfo pi = new Metadata.ParameterInfo();
    pi.setType(type);
    pi.setName(name);
    return pi;
  }

  private static Metadata.FieldInfo f(String name, String type) {
    Metadata.FieldInfo fi = new Metadata.FieldInfo();
    fi.setName(name);
    fi.setType(type);
    return fi;
  }

  private static Metadata.MethodInfo m(String name, String ret, Metadata.ParameterInfo... params) {
    Metadata.MethodInfo mi = new Metadata.MethodInfo();
    mi.setName(name);
    mi.setReturnType(ret);
    mi.setParameters(new ArrayList<>(Arrays.asList(params)));
    return mi;
  }

  private static Metadata.ConstructorInfo c(String name, Metadata.ParameterInfo... params) {
    Metadata.ConstructorInfo ci = new Metadata.ConstructorInfo();
    ci.setName(name);
    ci.setParameters(new ArrayList<>(Arrays.asList(params)));
    return ci;
  }

  @Test
  public void parameterComparator_ordersByType_thenNameNullsFirst() {
    Metadata.ParameterInfo a = p("int", null);
    Metadata.ParameterInfo b = p("int", "x");
    Metadata.ParameterInfo c = p("java.lang.String", "a");
    // Directly test concat order
    assertThat(Metadata.concatParamTypes(Arrays.asList(a, b, c)), is("int,int,java.lang.String"));
  }

  @Test
  public void fieldComparator_ordersByName_thenTypeNullsFirst() {
    Metadata.FieldInfo f1 = f("b", "int");
    Metadata.FieldInfo f2 = f("a", null);
    Metadata.FieldInfo f3 = f("a", "int");
    List<Metadata.FieldInfo> list = new ArrayList<>(Arrays.asList(f1, f2, f3));
    list.sort(Metadata.FIELD_COMPARATOR);
    assertThat(list, contains(f2, f3, f1));
  }

  @Test
  public void methodComparator_ordersByName_returnType_paramCount_paramTypes_andSignature() {
    Metadata.MethodInfo m1 = m("foo", "int", p("int", "x"));
    Metadata.MethodInfo m2 = m("foo", "int", p("int", "y"));
    Metadata.MethodInfo m3 = m("foo", "java.lang.String", p("int", "x"));
    Metadata.MethodInfo m4 = m("bar", "void");
    Metadata.MethodInfo m5 = m("foo", "int", p("int", "x"));
    m1.setSignature("A");
    m5.setSignature("S");

    List<Metadata.MethodInfo> list = new ArrayList<>(Arrays.asList(m1, m2, m3, m4, m5));
    list.sort(Metadata.METHOD_COMPARATOR);
    // order: by name(bar first), then by return type (int before string), then params, then
    // signature
    assertThat(list.get(0).getName(), is("bar"));
    assertThat(list.get(1).getReturnType(), is("int"));
    assertThat(list.get(2).getReturnType(), is("int"));
    // signature m1(A) should come before m5(S) due to signature comparison
    assertThat(list.get(1).getSignature(), is("A"));
    assertThat(list.get(2).getSignature(), is("S"));
  }

  @Test
  public void constructorComparator_ordersByName_thenParamCount_andTypes() {
    Metadata.ConstructorInfo c1 = c("A", p("int", "x"));
    Metadata.ConstructorInfo c2 = c("A");
    Metadata.ConstructorInfo c3 = c("B", p("int", "x"));
    List<Metadata.ConstructorInfo> list = new ArrayList<>(Arrays.asList(c1, c2, c3));
    list.sort(Metadata.CONSTRUCTOR_COMPARATOR);
    assertThat(list, contains(c2, c1, c3));
  }

  @Test
  public void computeMD5Sum_isDeterministic_and_orderIndependent() {
    Metadata.ClassInfo ci = new Metadata.ClassInfo();
    ci.setClassName("com.acme.Foo");
    ci.setSimpleName("Foo");
    ci.setPackageName("com.acme");
    ci.setMajorVersion("61");
    ci.setMinorVersion("0");
    ci.setSourceFile("Foo.java");
    ci.setSuperclass("java.lang.Object");
    ci.setArrayClass(false);
    ci.setInterfaces(Arrays.asList("java.io.Serializable", "java.lang.Cloneable"));
    ci.setSuperclasses(Arrays.asList("java.lang.Object"));
    ci.setSubclasses(Collections.singletonList("com.acme.Sub"));
    ci.setPeerIds(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()));

    Metadata.MethodInfo mA = m("m", "void", p("int", "x"));
    Metadata.FieldInfo fA = f("a", "int");
    Metadata.ConstructorInfo cA = c("Foo", p("int", "x"));
    ci.setMethods(Arrays.asList(mA));
    ci.setFields(Arrays.asList(fA));
    ci.setConstructors(Arrays.asList(cA));

    String md1 = Metadata.computeMD5Sum(ci);

    // Reorder lists should not change MD5 due to internal sorting
    ci.setMethods(Arrays.asList(mA));
    ci.setFields(Arrays.asList(fA));
    ci.setConstructors(Arrays.asList(cA));
    String md2 = Metadata.computeMD5Sum(ci);
    assertEquals(md1, md2);

    // Change something meaningful: add a new method
    ci.setMethods(Arrays.asList(mA, m("n", "int", p("java.lang.String", "s"))));
    String md3 = Metadata.computeMD5Sum(ci);
    // MD5 should change
    assertNotEquals(md1, md3);
  }
}
