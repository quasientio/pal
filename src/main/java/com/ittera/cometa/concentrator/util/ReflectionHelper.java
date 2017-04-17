package com.ittera.cometa.concentrator.util;

import org.apache.commons.lang3.ClassUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * TODO: Is the use of isAssignable (from commons.lang3.ClassUtils) safe, considering the support for widening?
 * We should avoid that if 2 or more methods match (i.e. all params assignable), the method with the least specific type are chosen
 * TODO: WE MUST UNIT TEST THIS CLASS
 */
public final class ReflectionHelper {

  protected static final Logger logger = LogManager.getLogger(ReflectionHelper.class);

  private ReflectionHelper() {
    //avoid instantiation
  }

  /**
   * Gets the right method when a parameter is a subtype of a method's formal parameter type
   * (based on http://stackoverflow.com/a/2580699)
   *
   * @param clazz
   * @param parameters
   * @param methodName
   * @return
   */
  public static Method getMethodToInvoke(Class clazz, Object[] parameters, String methodName) {
    logger.traceEntry("w/ class:{} and method:{}", clazz.getName(), methodName);

    for (Method method : clazz.getDeclaredMethods()) {
      if (!method.getName().equals(methodName)) {
        continue;
      }
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length != parameters.length) {
        continue;
      }

      boolean matches = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!ClassUtils.isAssignable(parameters[i].getClass(), parameterTypes[i])) {
          matches = false;
          break;
        }
      }
      if (matches) {
        logger.debug("Found method with signature: {}", method.getParameterTypes());
        return method;
      }
    }

    if ((parameters != null) && logger.isWarnEnabled()) {
      final StringBuilder stringBuilder = new StringBuilder();
      for (int i = 0; i < parameters.length; i++) {
        stringBuilder.append("params[").append(i).append("]=").append(parameters[i]).append('\n');
      }
      logger.warn("No matching method found for name:{} and parameters:\n{}", methodName, stringBuilder.toString());
    }

    return null;
  }
}
