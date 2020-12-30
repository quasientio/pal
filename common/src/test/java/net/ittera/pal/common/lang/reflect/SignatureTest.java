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

package net.ittera.pal.common.lang.reflect;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class SignatureTest {

  static class DummyClassForSignatureTest {
    public DummyClassForSignatureTest() {}
  }

  protected Class declaringType;
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
