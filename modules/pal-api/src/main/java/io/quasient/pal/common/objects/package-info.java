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
 * Remote object reference abstractions.
 *
 * <p>{@link ObjectRef} represents a reference to an object that may live on a remote peer. When
 * objects are passed across peer boundaries, they are wrapped in ObjectRef to enable transparent
 * remote method invocation.
 */
package io.quasient.pal.common.objects;
