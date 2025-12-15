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
 * Categories of code elements that PAL can intercept and convert to messages.
 *
 * <p>PAL captures three types of operations: constructor invocations, method calls, and field
 * accesses. This enum identifies which category a given signature or message belongs to.
 */
public enum ExecutableObjectType {

  /** A constructor invocation ({@code new ClassName(...)}). */
  CONSTRUCTOR,

  /** A method call ({@code object.method(...)} or {@code ClassName.staticMethod(...)}). */
  METHOD,

  /** A field access (read or write). */
  FIELD
}
