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
package io.quasient.pal.common.runtime;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Represents a dispatcher responsible for executing operations from a quantized context.
 *
 * @see Context
 */
public interface Dispatcher {

  /**
   * Entry point for the hot-path, i.e. execution of quantized operations (constructor/method calls
   * and field ops).
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the dispatch operation, or null for void operations
   * @throws Throwable if an error occurs during the dispatch process
   */
  Object dispatch(ProceedingJoinPoint pjp) throws Throwable;
}
