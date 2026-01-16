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
 * etcd-based directory service client implementation.
 *
 * <p>{@link PalDirectory} is the main entry point for interacting with the PAL directory:
 *
 * <ul>
 *   <li>Peer registration and discovery
 *   <li>Log metadata management
 *   <li>Intercept registration and watching
 * </ul>
 *
 * @see PalDirectory Main directory service interface
 * @see PeerLease Peer registration lease management
 */
package io.quasient.pal.cxn.directory;
