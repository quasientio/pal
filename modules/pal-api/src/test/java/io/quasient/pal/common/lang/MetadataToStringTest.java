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

import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class MetadataToStringTest {

  @Test
  public void toString_variants_cover_nested_classes() {
    Metadata.ParameterInfo pi = new Metadata.ParameterInfo();
    pi.setName("x");
    pi.setType("int");
    String pit = pi.toString();
    assertTrue(pit.contains("type='int'"));

    Metadata.ConstructorInfo ci = new Metadata.ConstructorInfo();
    ci.setName("C");
    ci.setParameters(Collections.singletonList(pi));
    String cit = ci.toString();
    assertTrue(cit.contains("ConstructorInfo"));

    Metadata.MethodInfo mi = new Metadata.MethodInfo();
    mi.setName("m");
    mi.setReturnType("void");
    mi.setParameters(Collections.singletonList(pi));
    String mit = mi.toString();
    assertTrue(mit.contains("MethodInfo"));

    Metadata.FieldInfo fi = new Metadata.FieldInfo();
    fi.setName("f");
    fi.setType("int");
    String fit = fi.toString();
    assertTrue(fit.contains("FieldInfo"));

    Metadata.ClassInfo cl = new Metadata.ClassInfo();
    cl.setClassName("com.acme.Foo");
    cl.setSimpleName("Foo");
    cl.setPackageName("com.acme");
    cl.setSourceFile("Foo.java");
    cl.setMethods(Collections.singletonList(mi));
    cl.setFields(Collections.singletonList(fi));
    cl.setConstructors(Collections.singletonList(ci));
    String clt = cl.toString();
    assertTrue(clt.contains("ClassInfo"));
    assertTrue(clt.contains("methods"));
  }
}
