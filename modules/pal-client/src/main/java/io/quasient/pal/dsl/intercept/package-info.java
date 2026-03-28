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
 * Domain-specific language for defining and managing intercept bundles.
 *
 * <p>This package provides high-level model classes for specifying intercept definitions
 * programmatically or via YAML. The key abstractions are:
 *
 * <ul>
 *   <li>{@link io.quasient.pal.dsl.intercept.InterceptSpec} - A single intercept definition with a
 *       builder for programmatic construction.
 *   <li>{@link io.quasient.pal.dsl.intercept.InterceptBundleSpec} - A named collection of intercept
 *       specs with shared defaults.
 *   <li>{@link io.quasient.pal.dsl.intercept.InterceptBundleDefaults} - Default values inherited by
 *       all intercepts in a bundle.
 *   <li>{@link io.quasient.pal.dsl.intercept.ApplyResult} - Result of applying a bundle of
 *       intercepts.
 *   <li>{@link io.quasient.pal.dsl.intercept.RemoveResult} - Result of removing intercepts.
 *   <li>{@link io.quasient.pal.dsl.intercept.InterceptDiff} - Diff entry for dry-run comparison.
 *   <li>{@link io.quasient.pal.dsl.intercept.BundleStatus} - Status of a bundle's intercepts.
 *   <li>{@link io.quasient.pal.dsl.intercept.BundleMetadata} - Lightweight metadata stored per
 *       bundle.
 * </ul>
 *
 * <p>The {@link io.quasient.pal.dsl.intercept.InterceptSpec#toInterceptRequest} method bridges the
 * high-level DSL with the low-level {@link io.quasient.pal.common.directory.nodes.InterceptRequest}
 * API.
 *
 * @see io.quasient.pal.common.lang.intercept.InterceptType
 * @see io.quasient.pal.common.directory.nodes.InterceptRequest
 */
package io.quasient.pal.dsl.intercept;
