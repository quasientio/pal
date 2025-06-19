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

package com.quasient.pal.common.lang.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class ParamsTest {

  static class DummyClass {
    public void increment(int ignoredA, int ignoredB) {}
  }

  private String[] parameterNames;
  private Class<?>[] parameterTypes;
  private Parameter[] parameters;
  private Params params;

  @Before
  public void setUp() throws Exception {
    parameterNames = new String[] {"param1", "param2"};
    parameterTypes = new Class[] {String.class, String.class};
    Method method =
        CodeSignatureTest.DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    parameters = method.getParameters();
    params = new Params(parameterNames, parameterTypes, parameters);
  }

  @Test
  public void params_differentLenArrays_illegalArgumentException() throws NoSuchMethodException {
    String[] parameterNames = new String[] {"param1", "param2"};

    // parameterTypes should be of len=2
    Class<?>[] parameterTypes = new Class[] {String.class};
    Method method =
        CodeSignatureTest.DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    Parameter[] parameters = method.getParameters();
    try {
      new Params(parameterNames, parameterTypes, parameters);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException ignored) {
      // all good
    }
  }

  @Test
  public void params_noParamNames_argX() {
    Params paramsNoNames = new Params(null, parameterTypes, parameters);
    assertThat(paramsNoNames.getParameterNames(), is(new String[] {"arg0", "arg1"}));
  }

  @Test
  public void getParameterNames() {
    assertThat(params.getParameterNames(), is(parameterNames));
  }

  @Test
  public void getParameterTypes() {
    assertThat(params.getParameterTypes(), is(parameterTypes));
  }

  @Test
  public void getParameters() {
    assertThat(params.getParameters(), is(parameters));
  }

  @Test
  public void equalsContract() throws NoSuchMethodException {
    // EqualsVerifier requires prefabValues with actual instances of Parameter.
    // Since we can't instantiate Parameter, we use our DummyClass and reflection to get them.
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    EqualsVerifier.forClass(Params.class)
        .withPrefabValues(Parameter.class, method.getParameters()[0], method.getParameters()[1])
        .usingGetClass()
        .verify();
  }
}
