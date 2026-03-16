/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
