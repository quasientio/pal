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

package com.quasient.pal.core;

import static java.lang.String.format;

import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.After;
import com.quasient.pal.common.lang.intercept.Before;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import io.etcd.jetcd.kv.PutResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes intercept annotations by scanning target class methods for {@link Before} and {@link
 * After} annotations and registering corresponding intercept requests with a directory service.
 */
@Singleton
public class InterceptAnnotationProcessor {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptAnnotationProcessor.class);

  /** Unique identifier representing the current peer instance. */
  private final UUID peerUuid;

  /** Provider for obtaining directory connections to register intercept requests. */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /**
   * Constructs an InterceptAnnotationProcessor with the specified peer identifier and directory
   * connection provider.
   *
   * @param peerUuid the unique identifier for this peer instance
   * @param directoryConnectionProvider the provider used for directory connections when registering
   *     intercept requests
   */
  @Inject
  InterceptAnnotationProcessor(
      UUID peerUuid, DirectoryConnectionProvider directoryConnectionProvider) {
    this.peerUuid = peerUuid;
    this.directoryConnectionProvider = directoryConnectionProvider;
  }

  /**
   * Inspects the declared methods of the specified class for intercept annotations and registers
   * intercept requests. This method scans each method of the provided class for the presence of
   * {@link Before} and {@link After} annotations. For each encountered annotation, it extracts the
   * required metadata via reflection and constructs an intercept request, which is then registered
   * with the directory service.
   *
   * @param clazz the class whose methods are to be inspected for intercept annotations; must not be
   *     null
   */
  public void process(Class<?> clazz) {
    if (logger.isTraceEnabled()) {
      logger.trace("inspecting class '{}' for annotations", clazz.getName());
    }

    List<Class<? extends Annotation>> annotationClasses = Arrays.asList(Before.class, After.class);
    // collect annotations and batch messages
    for (Method method : clazz.getDeclaredMethods()) {
      for (var annotationClass : annotationClasses) {
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
            if (logger.isTraceEnabled()) {
              logger.trace(
                  "interceptableClassName: {}, interceptableMethodName: {}, parameterTypes: {},"
                      + " interceptableFieldName: {}, interceptableFieldOpType: {}",
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
                          interceptableFieldName, FieldOpType.valueOf(interceptableFieldOpType))));
        }
      }
    }

    // TODO process @After annotation
  }

  /**
   * Determines the intercept type associated with the provided annotation class.
   *
   * @param annotationClass the class of the intercept annotation (e.g. {@link Before}, {@link
   *     After})
   * @return the corresponding {@link InterceptType} for the given annotation class
   * @throws IllegalArgumentException if the provided annotation class is not supported
   */
  private static InterceptType getTypeForAnnotationClass(
      Class<? extends Annotation> annotationClass) {
    if (annotationClass == Before.class) {
      return InterceptType.BEFORE;
    } else if (annotationClass == After.class) {
      return InterceptType.AFTER;
    } else {
      throw new IllegalArgumentException(
          "Unsupported annotation class: " + annotationClass.getName());
    }
  }

  /**
   * Registers the specified intercept request with the directory service. This method attempts to
   * obtain a directory connection from the provider and asynchronously registers the given
   * intercept request.
   *
   * @param interceptRequest the intercept request to register; must contain valid intercept
   *     metadata
   */
  private void register(InterceptRequest<?> interceptRequest) {
    try {
      Optional<PalDirectory> directory = directoryConnectionProvider.get();
      if (directory.isPresent()) {
        @SuppressWarnings("unused")
        PutResponse unusedResponse = directory.get().registerInterceptAsync(interceptRequest).get();
        logger.debug("Successfully registered new intercept request in directory");
      } else {
        logger.error("Pal Directory is not available");
      }
    } catch (Exception e) {
      logger.error("Error registering intercept request", e);
    }
  }
}
