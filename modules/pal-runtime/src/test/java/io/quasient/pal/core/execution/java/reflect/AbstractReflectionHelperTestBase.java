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
package io.quasient.pal.core.execution.java.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;

public abstract class AbstractReflectionHelperTestBase {
  protected Object invoke(Executable executable, Object[] args) throws Exception {
    if (executable.isVarArgs()) {
      int parameterCount = executable.getParameterTypes().length;
      Object lastArg = args[args.length - 1];

      if (!(lastArg instanceof Object[] || lastArg.getClass().isArray())) {
        Class<?> varargsType =
            executable.getParameterTypes()[parameterCount - 1].getComponentType();
        Object varargsArray = Array.newInstance(varargsType, args.length - parameterCount + 1);

        for (int i = parameterCount - 1, j = 0; i < args.length; i++, j++) {
          Array.set(varargsArray, j, args[i]);
        }

        args = Arrays.copyOf(args, parameterCount);
        args[parameterCount - 1] = varargsArray;
      }
    }

    if (executable instanceof Method method) {
      return method.invoke(getTestClass().getDeclaredConstructor().newInstance(), args);
    } else if (executable instanceof Constructor<?> ctor) {
      executable.setAccessible(true);
      return ctor.newInstance(args);
    } else {
      throw new IllegalArgumentException("Unsupported Executable type");
    }
  }

  protected abstract Class<?> getTestClass();
}
