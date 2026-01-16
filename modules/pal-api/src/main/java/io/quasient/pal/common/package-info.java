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
