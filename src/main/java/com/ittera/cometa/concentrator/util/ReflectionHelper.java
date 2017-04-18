package com.ittera.cometa.concentrator.util;

import com.ittera.cometa.concentrator.messages.protobuf.data.Primitives;

import org.apache.commons.lang3.ClassUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Is the use of isAssignable (from commons.lang3.ClassUtils) safe, considering the support for widening?
 * We should avoid that if 2 or more methods match (i.e. all params assignable), the method with the least specific type are chosen
 * TODO: WE MUST UNIT TEST THIS CLASS
 */
public final class ReflectionHelper {

    protected static final Logger logger = LogManager.getLogger(ReflectionHelper.class);

    private static Map<String,Method> matchedMethodsCache = new ConcurrentHashMap<>();
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
    public static Method getMethodToInvoke(Class clazz, Object[] parameters, List<Primitives.Object> parameterTypeNames, String methodName) {
        logger.traceEntry("w/ class:{} and method:{}", clazz.getName(), methodName);

        if (parameters.length != parameterTypeNames.size()) {
            throw new IllegalArgumentException(String.format("Parameters length=%s, different from parameter types length=%s",
                    parameters.length, parameterTypeNames.size()));
        }

        // trace params
        if (parameters != null && logger.isTraceEnabled()) {
            final StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < parameters.length; i++) {
                stringBuilder.append("params[").append(i).append("]=").append(parameters[i]).append(" type:").append(parameterTypeNames.get(i)).append('\n');
            }
        }

        // cache lookup
        Method cached = lookup(methodName, parameterTypeNames);
        if (cached != null) {
            logger.debug("Got cached method with signature in step0: {}", cached);
            return cached;
        }

        // let's try the easy way
        try {
            // create type array
            Class[] parameterTypes = new Class[parameterTypeNames.size()];
            for (int i = 0; i < parameterTypeNames.size(); i++) {
                parameterTypes[i] = Class.forName(parameterTypeNames.get(i).getClass_().getName());
            }

            Method methodFound = clazz.getMethod(methodName, parameterTypes);
            cache(methodName, parameterTypeNames, methodFound);
            logger.debug("Got method with signature in step1: {}", methodFound);
            return methodFound;
        } catch (Exception e) {
           logger.debug("Could not find method the easy way - {}", e.getMessage());
        }


        // scan public methods
        for (Method method : clazz.getMethods()) {
            logger.trace("public method: {}", method.getName());
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
                cache(methodName, parameterTypeNames, method);
                logger.debug("Got method with signature in step2: {}", method);
                return method;
            }
        }

        // now scan other methods
        for (Method method : clazz.getDeclaredMethods()) {
            logger.trace("declared method: {}", method.getName());
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
                cache(methodName, parameterTypeNames, method);
                logger.debug("Got method with signature in step3: {}", method);
                return method;
            }
        }

        logger.warn("No matching method found for name:{}", methodName);

        return null;
    }

    private static void cache(String methodName, List<Primitives.Object> parameterTypeNames, Method method) {
       StringBuffer keyBuilder = new StringBuffer(methodName);
       for (Primitives.Object paramType: parameterTypeNames) {
           keyBuilder.append(paramType.getClass_().getName());
       }
       matchedMethodsCache.put(keyBuilder.toString(), method);
    }

    private static Method lookup(String methodName, List<Primitives.Object> parameterTypeNames) {
        StringBuffer keyBuilder = new StringBuffer(methodName);
        for (Primitives.Object paramType: parameterTypeNames) {
            keyBuilder.append(paramType.getClass_().getName());
        }
        return matchedMethodsCache.get(keyBuilder.toString());
    }
}
