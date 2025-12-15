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
 * Data structures stored in the PAL directory (etcd).
 *
 * <ul>
 *   <li>{@link PeerInfo} - Peer registration metadata (UUID, RPC endpoints, status)
 *   <li>{@link LogInfo} - Log metadata (name, backend type, partition info)
 *   <li>{@link InterceptRequest} - Dynamic intercept registration
 * </ul>
 *
 * <p>All nodes extend {@link InfoNode} which tracks creation and modification times.
 */
package com.quasient.pal.common.directory.nodes;
