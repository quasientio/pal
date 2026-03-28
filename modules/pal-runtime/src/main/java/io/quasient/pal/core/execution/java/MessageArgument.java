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
package io.quasient.pal.core.execution.java;

/**
 * Encapsulates a message argument for remote procedure calls.
 *
 * <p>This record carries an argument value along with an indicator whether the argument should be
 * passed by reference or by value. The {@code object} field holds the argument's value, and the
 * {@code byReference} flag determines the passing mechanism.
 *
 * @param object the argument value, which can be any object used in the RPC context
 * @param byReference true if the argument should be treated as passed by reference; false if passed
 *     by value
 */
public record MessageArgument(Object object, boolean byReference) {}
