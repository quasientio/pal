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
 * Connection and communication utilities for PAL clients.
 *
 * <p>This package provides client-side abstractions for connecting to PAL infrastructure:
 *
 * <ul>
 *   <li>{@link ThinPeer} - Lightweight peer client for RPC without full runtime
 *   <li>{@link JmxClient} - JMX client for peer monitoring
 * </ul>
 *
 * <p>Subpackages:
 *
 * <ul>
 *   <li>{@code directory} - etcd-based directory service client
 *   <li>{@code chronicle} - Chronicle Queue utilities
 * </ul>
 */
package com.quasient.pal.cxn;
