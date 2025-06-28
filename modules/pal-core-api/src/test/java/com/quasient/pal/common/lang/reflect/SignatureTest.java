/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.reflect;

import static org.junit.Assert.assertEquals;

import nl.jqno.equalsverifier.EqualsVerifier;
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

  @Before
  public void setUp() throws Exception {
    declaringType = DummyClassForSignatureTest.class;
    declaringTypeName = "DummyClassForSignatureTest";
    modifiers = 0;
    name = "DummyClassForSignatureTest";
    signature = new Signature(declaringType, declaringTypeName, modifiers, name) {};
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
    EqualsVerifier.forClass(Signature.class).usingGetClass().verify();
  }
}
