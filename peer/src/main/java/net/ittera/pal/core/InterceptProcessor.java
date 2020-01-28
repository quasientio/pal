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

package net.ittera.pal.core;

import static java.lang.String.format;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.annotation.After;
import net.ittera.pal.common.lang.annotation.Before;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.common.znodes.InterceptRequest;
import net.ittera.pal.cxn.PALDirectory;
import org.apache.curator.framework.api.CuratorEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InterceptProcessor {

  private static final Logger logger = LoggerFactory.getLogger(InterceptProcessor.class);
  private final UUID peerUuid;
  private final PALDirectory directory;

  @Inject
  InterceptProcessor(UUID peerUuid, PALDirectory directory) {
    this.peerUuid = peerUuid;
    this.directory = directory;
  }

  public void process(Class clazz) {
    if (logger.isDebugEnabled()) {
      logger.debug("inspecting class '{}' for annotations", clazz.getName());
    }

    List<Class> annotationClasses = Arrays.asList(Before.class, After.class);
    // collect annotations and batch messages
    for (Method method : clazz.getDeclaredMethods()) {
      for (Class annotationClass : annotationClasses) {
        Annotation annotation = method.getDeclaredAnnotation(annotationClass);
        InterceptType interceptType = getTypeForAnnotationClass(annotationClass);
        if (annotation != null) {
          Class<? extends Annotation> type = annotation.annotationType();
          String interceptableClassName;
          String interceptableMethodName;
          String interceptableFieldName;
          String interceptableFieldOpType;
          List<String> parameterTypes;
          try {
            // extract annotation info
            interceptableClassName =
                (String) type.getDeclaredMethod("clazz").invoke(annotation, (Object[]) null);
            interceptableMethodName =
                (String) type.getDeclaredMethod("method").invoke(annotation, (Object[]) null);
            // parameter types are extracted from the callback signature
            parameterTypes =
                Arrays.stream(method.getParameterTypes())
                    .map(Class::getName)
                    .collect(Collectors.toList());
            interceptableFieldName =
                (String) type.getDeclaredMethod("field").invoke(annotation, (Object[]) null);
            interceptableFieldOpType =
                (String) type.getDeclaredMethod("fieldOpType").invoke(annotation, (Object[]) null);
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "interceptableClassName: {}, interceptableMethodName: {}, parameterTypes: {}, interceptableFieldName: {}, interceptableFieldOpType: {}",
                  interceptableClassName,
                  interceptableMethodName,
                  parameterTypes,
                  interceptableFieldName,
                  interceptableFieldOpType);
            }
          } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error(
                format(
                    "Error processing annotation '%s' found in method '%s' of class '%s",
                    annotation, method.getName(), clazz.getName()),
                e);
            continue;
          }

          // create and register new intercept request
          boolean isMethodInterceptable = interceptableMethodName != null;
          register(
              new InterceptRequest<>(
                  UUID.randomUUID(), // request uuid
                  peerUuid,
                  interceptType,
                  interceptableClassName,
                  clazz.getName(), // callback class
                  method.getName(), // callback method
                  isMethodInterceptable
                      ? new InterceptableMethodCall(interceptableMethodName, parameterTypes)
                      : new InterceptableFieldOp(
                          interceptableFieldName,
                          FieldOpType.fromString(interceptableFieldOpType))));
        }
      }
    }

    // TODO process @After annotation
  }

  private static InterceptType getTypeForAnnotationClass(Class annotationClass) {
    if (annotationClass == Before.class) {
      return InterceptType.BEFORE;
    } else if (annotationClass == After.class) {
      return InterceptType.AFTER;
    } else {
      throw new IllegalArgumentException(
          "Unsupported annotation class: " + annotationClass.getName());
    }
  }

  private void register(InterceptRequest interceptRequest) {
    try {
      directory.registerInterceptAsync(
          interceptRequest,
          (curatorFramework, curatorEvent) -> {
            if (curatorEvent.getType().equals(CuratorEventType.CREATE)
                && curatorEvent.getResultCode() == 0) {
              if (logger.isDebugEnabled()) {
                logger.debug("Successfully registered new intercept request in directory");
              }
            } else {
              logger.warn("Wrong event or result code when trying to register intercept request");
            }
          });
    } catch (Exception e) {
      logger.error("Error registering intercept request", e);
    }
  }
}
