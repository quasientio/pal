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
 * Core abstractions shared across PAL components.
 *
 * <p>This package and its subpackages define the fundamental building blocks used by both PAL
 * runtime and client code:
 *
 * <ul>
 *   <li>{@link io.quasient.pal.common.lang} - Language-level constructs for reflection and
 *       interception
 *   <li>{@link io.quasient.pal.common.directory} - Peer and log registration via etcd
 *   <li>{@link io.quasient.pal.common.runtime} - Runtime context and execution environment
 *   <li>{@link io.quasient.pal.common.util} - General-purpose utilities
 * </ul>
 */
package io.quasient.pal.common;
