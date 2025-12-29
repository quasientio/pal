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
 * Remote object reference management and lifecycle.
 *
 * <p>When objects are passed across peer boundaries, they are assigned an {@link
 * io.quasient.pal.common.objects.ObjectRef} and stored in an {@link ObjectLookupStore}. This
 * enables transparent remote method invocation on objects that live on remote peers.
 *
 * <ul>
 *   <li>{@link ObjectLookupStore} - Interface for object storage and lookup
 *   <li>{@link ConcurrentHashMapObjectLookupStore} - Thread-safe implementation
 *   <li>{@link ObjectLookupStoreCleaner} - Removes unreferenced objects
 * </ul>
 */
package io.quasient.pal.core.runtime.objects;
