/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code @Before} annotation is used for method interception within the PAL runtime.
 *
 * <p>This annotation allows developers to define methods that should be executed before the
 * execution of specified target methods. It supports Ant-style path matching for specifying target
 * methods, leveraging Spring's AntPathMatcher for pattern matching. For more details on the
 * Ant-style path matching, refer to <a
 * href="https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html">
 * Spring's AntPathMatcher</a>.
 *
 * <p>Usage Example:
 *
 * <pre>{@code
 * @Before(clazz = "com.example.MyClass", method = "targetMethod", parameterTypes = {"java.lang.String"})
 * public void beforeTargetMethod() {
 *     // Interception logic here
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {

  /**
   * Specifies the fully qualified name of the target class whose method is to be intercepted.
   *
   * @return the name of the target class
   */
  String clazz();

  /**
   * Specifies the name of the target method to be intercepted. If not provided, all methods of the
   * target class are considered.
   *
   * @return the name of the target method, or an empty string if not specified
   */
  String method() default "";

  /**
   * Defines the parameter types of the target method to be intercepted. This allows for overloading
   * resolution by specifying the exact parameter signature.
   *
   * @return an array of parameter type names, or an empty array if not specified
   */
  String[] parameterTypes() default {};

  /**
   * Specifies the name of the field associated with the interception. This can be used to target
   * specific fields within the target class.
   *
   * @return the name of the target field, or an empty string if not specified
   */
  String field() default "";

  /**
   * Defines the type of operation on the specified field that should trigger the interception.
   * Common operation types include "get" or "put".
   *
   * @return the type of field operation to intercept, or an empty string if not specified
   */
  String fieldOpType() default "";
}
