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
 * PAL directory service abstractions for peer, log, and intercept registration.
 *
 * <p>The directory is backed by etcd and provides:
 *
 * <ul>
 *   <li>Peer registration and discovery
 *   <li>Log registration and metadata
 *   <li>Dynamic intercept registration
 *   <li>Watch notifications for changes
 * </ul>
 *
 * @see io.quasient.pal.common.directory.nodes Data structures stored in etcd
 * @see io.quasient.pal.common.directory.events Watch event types
 */
package io.quasient.pal.common.directory;
