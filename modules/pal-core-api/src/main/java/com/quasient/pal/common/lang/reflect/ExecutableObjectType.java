/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.reflect;

/**
 * Defines the types of executable objects that can be reflected upon within the Pal runtime.
 *
 * @see java.lang.reflect.Constructor
 * @see java.lang.reflect.Method
 * @see java.lang.reflect.Field
 */
public enum ExecutableObjectType {

  /**
   * Represents a constructor executable object.
   *
   * @see java.lang.reflect.Constructor
   */
  CONSTRUCTOR,

  /**
   * Represents a method executable object.
   *
   * @see java.lang.reflect.Method
   */
  METHOD,

  /**
   * Represents a field executable object.
   *
   * @see java.lang.reflect.Field
   */
  FIELD
}
