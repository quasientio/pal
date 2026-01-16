/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.Test;

public class SignatureTest {

  static class DummyClassForSignatureTest {
    public DummyClassForSignatureTest() {}
  }

  protected Class<?> declaringType;
  protected String declaringTypeName;
  protected int modifiers;
  protected String name;
  private Signature signature;

  private static class TestSignature extends Signature {
    public TestSignature(
        Class<?> declaringType, String declaringTypeName, int modifiers, String name) {
      super(declaringType, declaringTypeName, modifiers, name);
    }
  }

  @Before
  public void setUp() throws Exception {
    declaringType = DummyClassForSignatureTest.class;
    declaringTypeName = "DummyClassForSignatureTest";
    modifiers = 0;
    name = "DummyClassForSignatureTest";
    signature = new TestSignature(declaringType, declaringTypeName, modifiers, name);
  }

  @Test
  public void getDeclaringType() {
    assertEquals(declaringType, signature.getDeclaringType());
  }

  @Test
  public void getDeclaringTypeName() {
    assertEquals(declaringTypeName, signature.getDeclaringTypeName());
  }

  @Test
  public void getModifiers() {
    assertEquals(modifiers, signature.getModifiers());
  }

  @Test
  public void getName() {
    assertEquals(name, signature.getName());
  }

  @Test
  public void equalsContract() throws Exception {
    Signature a = new TestSignature(declaringType, declaringTypeName, modifiers, name);
    Signature b = new TestSignature(declaringType, declaringTypeName, modifiers, name);
    Signature c = new TestSignature(declaringType, declaringTypeName, modifiers, name);
    Signature different =
        new TestSignature(declaringType, declaringTypeName, modifiers, name + "x");

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }
}
