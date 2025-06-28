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
 * The {@code @After} annotation is used to intercept method executions after their completion.
 *
 * <p>This annotation allows defining interception behavior using Ant-style path patterns to match
 * specific classes and methods within the system. It facilitates the execution of additional logic
 * following the execution of targeted methods.
 *
 * <p>Ant-path matching is utilized for specifying the classes and methods to intercept. For more
 * details on the Ant-style path matching, refer to <a
 * href="https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html">
 * Spring's AntPathMatcher</a>.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @After(clazz = "com.example.service.UserService", method = "createUser")
 * public void afterUserCreation() {
 *     // Additional logic to execute after user creation
 * }
 * }</pre>
 *
 * @see Before
 * @see com.quasient.pal.common.lang.intercept.Around
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface After {

  /**
   * Specifies the fully qualified name of the class containing the method to intercept.
   *
   * @return the class name pattern to match for interception
   */
  String clazz();

  /**
   * Specifies the name of the method to intercept within the target class.
   *
   * <p>If omitted or left empty, all methods within the specified class will be intercepted.
   *
   * @return the method name to match for interception, or an empty string to match all methods
   */
  String method() default "";

  /**
   * Specifies the name of the field associated with the method to intercept.
   *
   * <p>This can be used to target methods that interact with specific fields within the class. If
   * omitted or left empty, the interception is not limited by field interactions.
   *
   * @return the field name to associate with the method interception, or an empty string if not
   *     applicable
   */
  String field() default "";

  /**
   * Specifies the argument patterns for the method to intercept.
   *
   * <p>Each argument pattern can use wildcards to match method parameters. If empty, the
   * interception applies regardless of method parameters.
   *
   * @return an array of argument patterns to match method parameters, or an empty array to match
   *     any
   */
  String[] args() default {};
}
