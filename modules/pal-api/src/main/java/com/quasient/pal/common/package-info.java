/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

/**
 * Core abstractions shared across PAL components.
 *
 * <p>This package and its subpackages define the fundamental building blocks used by both PAL
 * runtime and client code:
 *
 * <ul>
 *   <li>{@link com.quasient.pal.common.lang} - Language-level constructs for reflection and
 *       interception
 *   <li>{@link com.quasient.pal.common.directory} - Peer and log registration via etcd
 *   <li>{@link com.quasient.pal.common.runtime} - Runtime context and execution environment
 *   <li>{@link com.quasient.pal.common.util} - General-purpose utilities
 * </ul>
 */
package com.quasient.pal.common;
