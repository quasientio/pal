/**
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
package io.quasient.pal.weave;

import io.quasient.pal.common.runtime.DispatchForwarder;

/**
 * Companion aspect that softens checked exceptions from {@link DispatchForwarder} calls.
 *
 * <p>AspectJ's "declare soft" mechanism wraps checked exceptions in {@link
 * org.aspectj.lang.SoftException}, allowing the {@link FullQuantizeAspect} advice to propagate
 * exceptions without requiring explicit throws clauses on every intercepted method.
 *
 * <p>This aspect must be woven together with {@link FullQuantizeAspect} for proper operation.
 */
public aspect FullQuantizeSoftening {
  declare soft: Throwable : call (Object DispatchForwarder.constructor(..));
  declare soft: Throwable : call (void DispatchForwarder.voidInstanceMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidInstanceMethod(..));
  declare soft: Throwable : call (void DispatchForwarder.voidClassMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidClassMethod(..));
}