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
package io.quasient.pal.core.bench;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Encapsulates the arguments for calls being benchmarked.
 *
 * @param target the target object on which the method is invoked; null for static method calls
 * @param args the arguments for the method
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Benchmark data class - args array shared for performance")
@SuppressWarnings("ArrayRecordComponent")
public record InvocationArgs(Object target, Object[] args) {}
