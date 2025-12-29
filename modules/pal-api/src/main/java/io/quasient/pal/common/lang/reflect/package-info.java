/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

/**
 * Reflection-based signature types for methods, constructors, and fields.
 *
 * <p>These signatures wrap Java reflection objects ({@link java.lang.reflect.Method}, {@link
 * java.lang.reflect.Constructor}, {@link java.lang.reflect.Field}) and are used throughout PAL to
 * identify the target of an operation in messages and interception rules.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link io.quasient.pal.common.lang.reflect.MethodSignature} - Identifies a method
 *   <li>{@link io.quasient.pal.common.lang.reflect.ConstructorSignature} - Identifies a constructor
 *   <li>{@link io.quasient.pal.common.lang.reflect.FieldSignature} - Identifies a field
 * </ul>
 */
package io.quasient.pal.common.lang.reflect;
