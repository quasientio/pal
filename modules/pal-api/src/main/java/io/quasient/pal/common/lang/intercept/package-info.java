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
 * Dynamic interception mechanism for PAL operations.
 *
 * <p>Interception allows external code to observe and modify method calls, constructor invocations,
 * and field accesses at runtime. Intercepts are registered via etcd and matched against operations
 * using class/method patterns.
 *
 * <h2>Intercept Types</h2>
 *
 * <ul>
 *   <li>{@link InterceptType#BEFORE} - Callback invoked before method execution
 *   <li>{@link InterceptType#AFTER} - Callback invoked after method execution
 *   <li>{@link InterceptType#AROUND} - Callback wraps method execution, can skip or modify
 * </ul>
 *
 * <h2>Writing Callbacks</h2>
 *
 * <p>Implement {@link InterceptCallback} to handle intercepts:
 *
 * <pre>{@code
 * public class MyCallback implements InterceptCallback {
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // Inspect operation
 *         String methodName = ctx.getExec().getMethodName();
 *
 *         // Modify arguments (BEFORE phase)
 *         if (ctx.getPhase() == InterceptPhase.BEFORE) {
 *             ctx.setArg(0, newValue);
 *         }
 *
 *         // Modify return value (AFTER phase)
 *         if (ctx.getPhase() == InterceptPhase.AFTER) {
 *             ctx.setReturnValue(transformedValue);
 *         }
 *
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * @see InterceptCallback The functional interface for handling intercepts
 * @see InterceptContext Context object providing access to operation metadata and mutation methods
 * @see InterceptCallbackResponse Response controlling execution flow
 */
package io.quasient.pal.common.lang.intercept;
