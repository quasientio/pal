/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

/**
 * Represents a paired operation/completion span in the WAL, identified by the offsets of the
 * initiating operation entry and its corresponding completion entry.
 *
 * @param operationOffset the WAL offset of the operation (request) entry
 * @param completionOffset the WAL offset of the completion (response/done) entry
 */
public record Span(long operationOffset, long completionOffset) {}
