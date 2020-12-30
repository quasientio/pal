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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class CodeSignatureTest extends SignatureTest {

  static class DummyClass {
    public void increment(int a, int b) {}
  }

  protected Class[] exceptionTypes;
  protected String[] parameterNames;
  protected Class[] parameterTypes;
  protected Parameter[] parameters;
  private CodeSignature codeSignature;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    exceptionTypes = new Class[] {IllegalArgumentException.class};
    parameterNames = new String[] {"param1", "param2"};
    parameterTypes = new Class[] {String.class, String.class};
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    parameters = method.getParameters();
    codeSignature =
        new CodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters)) {};
  }

  @Test
  public void getExceptionTypes() {
    assertArrayEquals(exceptionTypes, codeSignature.getExceptionTypes());
  }

  @Test
  public void getParameterNames() {
    assertArrayEquals(parameterNames, codeSignature.getParameterNames());
  }

  @Test
  public void getParameterTypes() {
    assertArrayEquals(parameterTypes, codeSignature.getParameterTypes());
  }

  @Test
  public void getParameters() {
    assertArrayEquals(parameters, codeSignature.getParameters());
  }

  @Test
  public void equalsContract() throws NoSuchMethodException {
    // EqualsVerifier requires prefabValues with actual instances of Parameter.
    // Since we can't instantiate Parameter, we use our DummyClass and reflection to get them.
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    EqualsVerifier.forClass(CodeSignature.class)
        .withPrefabValues(Parameter.class, method.getParameters()[0], method.getParameters()[1])
        .usingGetClass()
        .verify();
  }
}
