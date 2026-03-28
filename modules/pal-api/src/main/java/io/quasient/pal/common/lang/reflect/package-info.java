/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
