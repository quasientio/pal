package net.ittera.pal.core.rpc.exec.java.reflect;

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

    if (executable instanceof Method) {
      return ((Method) executable)
          .invoke(getTestClass().getDeclaredConstructor().newInstance(), args);
    } else if (executable instanceof Constructor) {
      executable.setAccessible(true);
      return ((Constructor<?>) executable).newInstance(args);
    } else {
      throw new IllegalArgumentException("Unsupported Executable type");
    }
  }

  protected abstract Class<?> getTestClass();
}
