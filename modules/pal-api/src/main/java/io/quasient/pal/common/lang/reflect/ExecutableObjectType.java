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
package io.quasient.pal.common.lang.reflect;

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
